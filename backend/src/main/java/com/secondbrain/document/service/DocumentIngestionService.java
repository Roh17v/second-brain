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
import com.secondbrain.document.ingestion.TextChunker;
import com.secondbrain.document.ingestion.TextExtractor;
import com.secondbrain.document.mapper.DocumentMapper;
import com.secondbrain.document.repository.DocumentChunkRepository;
import com.secondbrain.document.repository.DocumentRepository;
import com.secondbrain.storage.FileStorageService;
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

	public DocumentIngestionService(
			DocumentRepository documentRepository,
			DocumentChunkRepository chunkRepository,
			DocumentMapper documentMapper,
			WorkspaceService workspaceService,
			FileStorageService fileStorageService,
			TextExtractor textExtractor,
			TextChunker textChunker
	) {
		this.documentRepository = documentRepository;
		this.chunkRepository = chunkRepository;
		this.documentMapper = documentMapper;
		this.workspaceService = workspaceService;
		this.fileStorageService = fileStorageService;
		this.textExtractor = textExtractor;
		this.textChunker = textChunker;
	}

	/**
	 * Parse stored file → chunk text → persist chunks. No embeddings yet.
	 */
	@Transactional
	public DocumentResponse process(UUID workspaceId, UUID documentId) {
		workspaceService.requireOwnedWorkspace(workspaceId);

		Document document = documentRepository
				.findByIdAndWorkspaceIdAndDeletedAtIsNull(documentId, workspaceId)
				.orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

		if ("pending".equals(document.getStoragePath())) {
			throw new BadRequestException("Document file is not available for processing");
		}

		document.setStatus(DocumentStatus.PROCESSING);
		document.setFailureReason(null);
		documentRepository.save(document);

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

			List<String> parts = textChunker.chunk(text);
			if (parts.isEmpty()) {
				throw new BadRequestException("Chunking produced no content");
			}

			// Replace previous chunks if re-processing
			chunkRepository.deleteByDocumentId(document.getId());
			chunkRepository.flush();

			List<DocumentChunk> entities = new ArrayList<>(parts.size());
			for (int i = 0; i < parts.size(); i++) {
				entities.add(new DocumentChunk(
						document.getId(),
						document.getWorkspaceId(),
						document.getOwnerId(),
						i,
						parts.get(i)
				));
			}
			chunkRepository.saveAll(entities);

			document.setStatus(DocumentStatus.READY);
			document.setFailureReason(null);
			Document saved = documentRepository.save(document);

			log.info(
					"Document {} processed: {} chunks (size={}, overlap={})",
					document.getId(),
					parts.size(),
					TextChunker.DEFAULT_CHUNK_SIZE,
					TextChunker.DEFAULT_CHUNK_OVERLAP
			);

			return documentMapper.toResponse(saved);
		}
		catch (RuntimeException ex) {
			document.setStatus(DocumentStatus.FAILED);
			String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
			if (reason.length() > 900) {
				reason = reason.substring(0, 900);
			}
			document.setFailureReason(reason);
			documentRepository.save(document);
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
						chunk.getCreatedAt()
				))
				.toList();
	}
}
