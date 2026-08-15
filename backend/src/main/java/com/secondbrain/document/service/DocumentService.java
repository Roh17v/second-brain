package com.secondbrain.document.service;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.secondbrain.common.exception.BadRequestException;
import com.secondbrain.common.exception.ResourceNotFoundException;
import com.secondbrain.common.exception.StorageException;
import com.secondbrain.document.dto.DocumentResponse;
import com.secondbrain.document.entity.Document;
import com.secondbrain.document.entity.DocumentStatus;
import com.secondbrain.document.mapper.DocumentMapper;
import com.secondbrain.document.repository.DocumentChunkRepository;
import com.secondbrain.document.repository.DocumentRepository;
import com.secondbrain.security.SecurityUtils;
import com.secondbrain.security.UserPrincipal;
import com.secondbrain.storage.FileStorageService;
import com.secondbrain.workspace.entity.Workspace;
import com.secondbrain.workspace.service.WorkspaceService;

@Service
public class DocumentService {

	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
			"pdf", "txt", "md", "markdown",
			"png", "jpg", "jpeg", "webp", "gif", "avif"
	);
	private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

	private final DocumentRepository documentRepository;
	private final DocumentChunkRepository chunkRepository;
	private final DocumentMapper documentMapper;
	private final WorkspaceService workspaceService;
	private final FileStorageService fileStorageService;
	private final DocumentIngestionPipeline documentIngestionPipeline;
	private final DocumentIngestProgress ingestProgress;

	public DocumentService(
			DocumentRepository documentRepository,
			DocumentChunkRepository chunkRepository,
			DocumentMapper documentMapper,
			WorkspaceService workspaceService,
			FileStorageService fileStorageService,
			DocumentIngestionPipeline documentIngestionPipeline,
			DocumentIngestProgress ingestProgress
	) {
		this.documentRepository = documentRepository;
		this.chunkRepository = chunkRepository;
		this.documentMapper = documentMapper;
		this.workspaceService = workspaceService;
		this.fileStorageService = fileStorageService;
		this.documentIngestionPipeline = documentIngestionPipeline;
		this.ingestProgress = ingestProgress;
	}

	@Transactional
	public DocumentResponse upload(UUID workspaceId, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BadRequestException("File is required");
		}
		if (file.getSize() > MAX_FILE_BYTES) {
			throw new BadRequestException("File exceeds maximum size of 20MB");
		}

		Workspace workspace = workspaceService.requireOwnedWorkspace(workspaceId);
		UserPrincipal currentUser = SecurityUtils.requireCurrentUser();

		String originalFilename = sanitizeFilename(file.getOriginalFilename());
		validateExtension(originalFilename);

		Document document = new Document(
				workspace.getId(),
				currentUser.getId(),
				originalFilename,
				originalFilename,
				file.getContentType(),
				file.getSize(),
				"pending",
				DocumentStatus.UPLOADED
		);
		document = documentRepository.saveAndFlush(document);

		try {
			String storagePath = fileStorageService.store(
					currentUser.getId(),
					workspace.getId(),
					document.getId(),
					originalFilename,
					file.getInputStream(),
					file.getSize()
			);
			document.setStoragePath(storagePath);
			document = documentRepository.save(document);

			// Queue OCR/chunk/embed only after this transaction commits.
			final UUID wsId = workspace.getId();
			final UUID docId = document.getId();
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					documentIngestionPipeline.processAndEmbedAsync(wsId, docId);
				}
			});

			return documentMapper.toResponse(document);
		}
		catch (IOException ex) {
			cleanupFailedUpload(document);
			throw new StorageException("Failed to read uploaded file", ex);
		}
		catch (RuntimeException ex) {
			cleanupFailedUpload(document);
			throw ex;
		}
	}

	/**
	 * Re-queue full process + embed (e.g. after FAILED).
	 */
	@Transactional
	public DocumentResponse retryIngestion(UUID workspaceId, UUID documentId) {
		workspaceService.requireOwnedWorkspace(workspaceId);
		Document document = documentRepository
				.findByIdAndWorkspaceIdAndDeletedAtIsNull(documentId, workspaceId)
				.orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

		if ("pending".equals(document.getStoragePath())) {
			throw new BadRequestException("Document file is not available");
		}

		document.setStatus(DocumentStatus.UPLOADED);
		document.setFailureReason(null);
		document.setChunkCount(null);
		document.setEmbeddedCount(0);
		document.setNotifyOnReady(false);
		document.setReadyNotifiedAt(null);
		document.setProcessingStartedAt(null);
		documentRepository.save(document);

		final UUID wsId = workspaceId;
		final UUID docId = documentId;
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				documentIngestionPipeline.reprocessAsync(wsId, docId);
			}
		});

		return documentMapper.toResponse(document);
	}

	@Transactional(readOnly = true)
	public List<DocumentResponse> listByWorkspace(UUID workspaceId) {
		workspaceService.requireOwnedWorkspace(workspaceId);
		return documentRepository
				.findByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(workspaceId)
				.stream()
				.map(this::toPolledResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public DocumentResponse getById(UUID workspaceId, UUID documentId) {
		workspaceService.requireOwnedWorkspace(workspaceId);
		Document document = documentRepository
				.findByIdAndWorkspaceIdAndDeletedAtIsNull(documentId, workspaceId)
				.orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
		return toPolledResponse(document);
	}

	private DocumentResponse toPolledResponse(Document document) {
		ingestProgress.promiseEmailIfLongProcess(document);
		return documentMapper.toResponse(document);
	}

	@Transactional
	public void softDelete(UUID workspaceId, UUID documentId) {
		workspaceService.requireOwnedWorkspace(workspaceId);
		Document document = documentRepository
				.findByIdAndWorkspaceIdAndDeletedAtIsNull(documentId, workspaceId)
				.orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
		document.softDelete();
		documentRepository.save(document);
		chunkRepository.deleteByDocumentId(documentId);
	}

	private void cleanupFailedUpload(Document document) {
		if (document.getId() != null) {
			fileStorageService.deleteQuietly(
					document.getOwnerId() + "/"
							+ document.getWorkspaceId() + "/"
							+ document.getId() + "/"
							+ document.getStoredFilename()
			);
			documentRepository.delete(document);
		}
	}

	static String sanitizeFilename(String originalFilename) {
		if (originalFilename == null || originalFilename.isBlank()) {
			throw new BadRequestException("Filename is required");
		}
		String name = originalFilename.replace('\\', '/');
		int slash = name.lastIndexOf('/');
		if (slash >= 0) {
			name = name.substring(slash + 1);
		}
		name = name.trim();
		if (name.isEmpty() || name.equals(".") || name.equals("..")) {
			throw new BadRequestException("Invalid filename");
		}
		name = name.replaceAll("[\\x00-\\x1F<>:\"|?*]", "_");
		if (name.length() > 200) {
			name = name.substring(name.length() - 200);
		}
		return name;
	}

	private static void validateExtension(String filename) {
		int dot = filename.lastIndexOf('.');
		if (dot < 0 || dot == filename.length() - 1) {
			throw new BadRequestException("File must have an extension (pdf, txt, md, png, jpg, …)");
		}
		String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
		if (!ALLOWED_EXTENSIONS.contains(ext)) {
			throw new BadRequestException(
					"Unsupported file type. Allowed: pdf, txt, md, png, jpg, jpeg, webp, gif, avif"
			);
		}
	}
}
