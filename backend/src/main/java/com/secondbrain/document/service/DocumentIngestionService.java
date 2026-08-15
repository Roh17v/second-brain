package com.secondbrain.document.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secondbrain.common.exception.BadRequestException;
import com.secondbrain.common.exception.ResourceNotFoundException;
import com.secondbrain.document.dto.DocumentChunkResponse;
import com.secondbrain.document.dto.DocumentResponse;
import com.secondbrain.document.entity.Document;
import com.secondbrain.document.entity.DocumentChunk;
import com.secondbrain.document.entity.DocumentStatus;
import com.secondbrain.document.ingestion.StructuredChunk;
import com.secondbrain.document.ingestion.TextChunker;
import com.secondbrain.document.ingestion.TextExtractor;
import com.secondbrain.document.mapper.DocumentMapper;
import com.secondbrain.document.repository.DocumentChunkRepository;
import com.secondbrain.document.repository.DocumentRepository;
import com.secondbrain.search.EmbedSpeedTracker;
import com.secondbrain.storage.FileStorageService;
import com.secondbrain.workspace.service.WorkspaceSearchIndexPurge;
import com.secondbrain.workspace.service.WorkspaceService;

@Service
public class DocumentIngestionService {

	private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

	private final DocumentRepository documentRepository;
	private final DocumentChunkRepository chunkRepository;
	private final DocumentMapper documentMapper;
	private final WorkspaceService workspaceService;
	private final FileStorageService fileStorageService;
	private final TextExtractor textExtractor;
	private final TextChunker textChunker;
	private final WorkspaceSearchIndexPurge searchIndexPurge;
	private final DocumentIngestProgress ingestProgress;
	private final EmbedSpeedTracker embedSpeedTracker;

	public DocumentIngestionService(
			DocumentRepository documentRepository,
			DocumentChunkRepository chunkRepository,
			DocumentMapper documentMapper,
			WorkspaceService workspaceService,
			FileStorageService fileStorageService,
			TextExtractor textExtractor,
			TextChunker textChunker,
			WorkspaceSearchIndexPurge searchIndexPurge,
			DocumentIngestProgress ingestProgress,
			EmbedSpeedTracker embedSpeedTracker
	) {
		this.documentRepository = documentRepository;
		this.chunkRepository = chunkRepository;
		this.documentMapper = documentMapper;
		this.workspaceService = workspaceService;
		this.fileStorageService = fileStorageService;
		this.textExtractor = textExtractor;
		this.textChunker = textChunker;
		this.searchIndexPurge = searchIndexPurge;
		this.ingestProgress = ingestProgress;
		this.embedSpeedTracker = embedSpeedTracker;
	}

	/**
	 * Parse stored file → chunk text → persist chunks. Sets status {@link DocumentStatus#EMBEDDING}.
	 * Requires authenticated owner (HTTP).
	 */
	@Transactional
	public DocumentResponse process(UUID workspaceId, UUID documentId) {
		workspaceService.requireOwnedWorkspace(workspaceId);
		return processInternal(workspaceId, documentId);
	}

	/**
	 * Same as {@link #process} without auth — for background pipeline after upload.
	 */
	@Transactional
	public DocumentResponse processInternal(UUID workspaceId, UUID documentId) {
		Document document = documentRepository
				.findByIdAndWorkspaceIdAndDeletedAtIsNull(documentId, workspaceId)
				.orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

		if ("pending".equals(document.getStoragePath())) {
			throw new BadRequestException("Document file is not available for processing");
		}

		// Commit PROCESSING immediately so polls leave "Queued" during OCR.
		ingestProgress.markProcessingStarted(document.getId());
		document.setStatus(DocumentStatus.PROCESSING);
		document.setFailureReason(null);
		document.setProcessingStartedAt(java.time.Instant.now());
		document.setChunkCount(null);
		document.setEmbeddedCount(0);
		document.setNotifyOnReady(false);
		document.setReadyNotifiedAt(null);

		try {
			String text;
			try (InputStream in = fileStorageService.open(document.getStoragePath())) {
				text = textExtractor.extract(document.getOriginalFilename(), in);
			}
			catch (java.io.IOException ex) {
				throw new com.secondbrain.common.exception.StorageException(
						"Failed to read stored document file",
						ex
				);
			}

			if (text == null || text.isBlank()) {
				throw new BadRequestException("No extractable text found in document");
			}

			List<StructuredChunk> parts = textChunker.chunkDocument(text);
			if (parts.isEmpty()) {
				throw new BadRequestException("Chunking produced no content");
			}

			// Replace previous chunks if re-processing
			chunkRepository.deleteByDocumentId(document.getId());
			chunkRepository.flush();

			int headed = 0;
			List<DocumentChunk> entities = new ArrayList<>(parts.size());
			for (int i = 0; i < parts.size(); i++) {
				var piece = parts.get(i);
				if (piece.sectionHeading() != null && !piece.sectionHeading().isBlank()) {
					headed++;
				}
				entities.add(new DocumentChunk(
						document.getId(),
						document.getWorkspaceId(),
						document.getOwnerId(),
						i,
						piece.content(),
						piece.sectionHeading()
				));
			}
			chunkRepository.saveAll(entities);

			if (searchIndexPurge.discardChunksIfNotSearchable(workspaceId, document.getId())) {
				return documentMapper.toResponse(document);
			}

			boolean notify = IngestEta.shouldNotify(parts.size(), embedSpeedTracker.averageMs());
			int updated = documentRepository.markEmbedding(document.getId(), parts.size(), notify);
			if (updated == 0) {
				chunkRepository.deleteByDocumentId(document.getId());
				return documentMapper.toResponse(document);
			}
			document.setStatus(DocumentStatus.EMBEDDING);
			document.setFailureReason(null);
			document.setChunkCount(parts.size());
			if (notify) {
				document.setNotifyOnReady(true);
			}

			log.info(
					"Document {} processed: {} chunks (headed={}, size={}, overlap={}) → EMBEDDING",
					document.getId(),
					parts.size(),
					headed,
					TextChunker.DEFAULT_CHUNK_SIZE,
					TextChunker.DEFAULT_CHUNK_OVERLAP
			);

			return documentMapper.toResponse(document);
		}
		catch (RuntimeException ex) {
			String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
			if (reason.length() > 900) {
				reason = reason.substring(0, 900);
			}
			ingestProgress.markFailed(document.getId(), reason);
			document.setStatus(DocumentStatus.FAILED);
			document.setFailureReason(reason);
			throw ex;
		}
	}

	@Transactional(readOnly = true)
	public List<DocumentChunkResponse> listChunks(UUID workspaceId, UUID documentId) {
		workspaceService.requireOwnedWorkspace(workspaceId);
		documentRepository
				.findByIdAndWorkspaceIdAndDeletedAtIsNull(documentId, workspaceId)
				.orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

		return chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId).stream()
				.map(chunk -> new DocumentChunkResponse(
						chunk.getId(),
						chunk.getDocumentId(),
						chunk.getChunkIndex(),
						chunk.getContent(),
						chunk.getContentLength(),
						chunk.getSectionHeading(),
						chunk.getCreatedAt()
				))
				.toList();
	}
}
