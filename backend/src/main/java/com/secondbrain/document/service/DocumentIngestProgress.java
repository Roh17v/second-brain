package com.secondbrain.document.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.secondbrain.document.entity.Document;
import com.secondbrain.document.entity.DocumentStatus;
import com.secondbrain.document.repository.DocumentRepository;

/**
 * Short, immediately-committed writes so the UI can poll ingest progress.
 */
@Service
public class DocumentIngestProgress {

	private static final Duration LONG_PROCESS = Duration.ofSeconds(IngestEta.EMAIL_THRESHOLD_SECONDS);

	private final DocumentRepository documentRepository;

	public DocumentIngestProgress(DocumentRepository documentRepository) {
		this.documentRepository = documentRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int markProcessingStarted(UUID documentId) {
		return documentRepository.markProcessingStarted(documentId, Instant.now());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int markEmbedding(UUID documentId, int chunkCount, boolean notifyOnReady) {
		return documentRepository.markEmbedding(documentId, chunkCount, notifyOnReady);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void setEmbeddedCount(UUID documentId, int embeddedCount) {
		documentRepository.updateEmbeddedCount(documentId, embeddedCount);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int markReady(UUID documentId) {
		return documentRepository.updateStatusIfNotDeleted(documentId, DocumentStatus.READY, null);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int markFailed(UUID documentId, String reason) {
		return documentRepository.updateStatusIfNotDeleted(documentId, DocumentStatus.FAILED, reason);
	}

	/**
	 * If OCR/queue has already run past the email threshold, promise mail now
	 * so the UI can say so before chunk count exists.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void promiseEmailIfLongProcess(Document document) {
		if (document == null || document.isNotifyOnReady() || document.getId() == null) {
			return;
		}
		DocumentStatus status = document.getStatus();
		if (status != DocumentStatus.PROCESSING && status != DocumentStatus.UPLOADED) {
			return;
		}
		Instant started = document.getProcessingStartedAt();
		if (started == null || Duration.between(started, Instant.now()).compareTo(LONG_PROCESS) <= 0) {
			return;
		}
		int updated = documentRepository.markNotifyOnReady(document.getId());
		if (updated > 0) {
			document.setNotifyOnReady(true);
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean markNotified(UUID documentId) {
		return documentRepository.markReadyNotified(documentId, Instant.now()) > 0;
	}
}
