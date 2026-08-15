package com.secondbrain.document.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.secondbrain.document.entity.Document;
import com.secondbrain.document.entity.DocumentStatus;
import com.secondbrain.document.repository.DocumentRepository;
import com.secondbrain.email.DocumentReadyMailer;
import com.secondbrain.search.EmbeddingService;

/**
 * Runs process (OCR/chunk) → embed automatically after upload.
 * Status transitions: UPLOADED → PROCESSING → EMBEDDING → READY | FAILED.
 */
@Service
public class DocumentIngestionPipeline {

	private static final Logger log = LoggerFactory.getLogger(DocumentIngestionPipeline.class);

	private final DocumentIngestionService documentIngestionService;
	private final EmbeddingService embeddingService;
	private final DocumentRepository documentRepository;
	private final DocumentReadyMailer documentReadyMailer;

	public DocumentIngestionPipeline(
			DocumentIngestionService documentIngestionService,
			EmbeddingService embeddingService,
			DocumentRepository documentRepository,
			DocumentReadyMailer documentReadyMailer
	) {
		this.documentIngestionService = documentIngestionService;
		this.embeddingService = embeddingService;
		this.documentRepository = documentRepository;
		this.documentReadyMailer = documentReadyMailer;
	}

	/**
	 * Full pipeline. Runs off the request thread (after DB commit).
	 * Does not re-check ownership — only called for just-uploaded owned docs.
	 */
	@Async("documentIngestionExecutor")
	public void processAndEmbedAsync(UUID workspaceId, UUID documentId) {
		log.info("Background ingestion started for document {}", documentId);
		try {
			// Internal methods: no SecurityContext on async threads.
			// Retry always re-runs process + embed from scratch.
			documentIngestionService.processInternal(workspaceId, documentId);
			embeddingService.embedDocumentInternal(workspaceId, documentId);
			log.info("Background ingestion finished READY for document {}", documentId);
			documentReadyMailer.notifyIfNeeded(documentId);
		}
		catch (Exception ex) {
			log.error("Background ingestion failed for document {}: {}", documentId, ex.getMessage(), ex);
			// process/embed already mark FAILED in most paths; ensure we don't leave stuck PROCESSING
			documentRepository.findById(documentId).ifPresent(doc -> {
				if (doc.getStatus() != DocumentStatus.FAILED && doc.getStatus() != DocumentStatus.READY) {
					doc.setStatus(DocumentStatus.FAILED);
					String reason = ex.getMessage() == null ? "Ingestion failed" : ex.getMessage();
					if (reason.length() > 900) {
						reason = reason.substring(0, 900);
					}
					doc.setFailureReason(reason);
					documentRepository.save(doc);
				}
			});
			documentReadyMailer.notifyIfNeeded(documentId);
		}
	}

	/**
	 * Re-run pipeline for an existing document (manual retry).
	 */
	@Async("documentIngestionExecutor")
	public void reprocessAsync(UUID workspaceId, UUID documentId) {
		processAndEmbedAsync(workspaceId, documentId);
	}
}
