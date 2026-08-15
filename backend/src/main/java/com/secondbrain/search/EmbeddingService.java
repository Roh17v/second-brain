package com.secondbrain.search;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secondbrain.ai.embedding.EmbeddingClient;
import com.secondbrain.ai.embedding.EmbeddingTask;
import com.secondbrain.common.exception.BadRequestException;
import com.secondbrain.common.exception.ResourceNotFoundException;
import com.secondbrain.document.dto.DocumentResponse;
import com.secondbrain.document.entity.Document;
import com.secondbrain.document.entity.DocumentChunk;
import com.secondbrain.document.entity.DocumentStatus;
import com.secondbrain.document.mapper.DocumentMapper;
import com.secondbrain.document.repository.DocumentChunkRepository;
import com.secondbrain.document.repository.DocumentRepository;
import com.secondbrain.document.service.DocumentIngestProgress;
import com.secondbrain.document.service.IngestEta;
import com.secondbrain.workspace.service.WorkspaceSearchIndexPurge;
import com.secondbrain.workspace.service.WorkspaceService;

import jakarta.annotation.PostConstruct;

@Service
public class EmbeddingService {

	private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

	/** Transient embedder blips (timeouts, 5xx) retry this many times per chunk. */
	static final int EMBED_ATTEMPTS = 3;

	private final EmbeddingClient embeddingClient;
	private final VectorStore vectorStore;
	private final HybridRetriever hybridRetriever;
	private final DocumentRepository documentRepository;
	private final DocumentChunkRepository chunkRepository;
	private final DocumentMapper documentMapper;
	private final WorkspaceService workspaceService;
	private final WorkspaceSearchIndexPurge searchIndexPurge;
	private final DocumentIngestProgress ingestProgress;
	private final EmbedSpeedTracker embedSpeedTracker;

	public EmbeddingService(
			EmbeddingClient embeddingClient,
			VectorStore vectorStore,
			HybridRetriever hybridRetriever,
			DocumentRepository documentRepository,
			DocumentChunkRepository chunkRepository,
			DocumentMapper documentMapper,
			WorkspaceService workspaceService,
			WorkspaceSearchIndexPurge searchIndexPurge,
			DocumentIngestProgress ingestProgress,
			EmbedSpeedTracker embedSpeedTracker
	) {
		this.embeddingClient = embeddingClient;
		this.vectorStore = vectorStore;
		this.hybridRetriever = hybridRetriever;
		this.documentRepository = documentRepository;
		this.chunkRepository = chunkRepository;
		this.documentMapper = documentMapper;
		this.workspaceService = workspaceService;
		this.searchIndexPurge = searchIndexPurge;
		this.ingestProgress = ingestProgress;
		this.embedSpeedTracker = embedSpeedTracker;
	}

	@PostConstruct
	void initSchema() {
		vectorStore.ensureSchema(embeddingClient.dimensions());
	}

	/**
	 * Embeds all chunks for a document. Requires authenticated owner (HTTP).
	 */
	public DocumentEmbedResponse embedDocument(UUID workspaceId, UUID documentId) {
		workspaceService.requireOwnedWorkspace(workspaceId);
		return embedDocumentInternal(workspaceId, documentId);
	}

	/**
	 * Same as {@link #embedDocument} without auth — for background pipeline.
	 * Not one transaction: each chunk's vector and {@code embedded_count} commit
	 * so the UI can poll progress.
	 */
	public DocumentEmbedResponse embedDocumentInternal(UUID workspaceId, UUID documentId) {
		Document document = documentRepository
				.findByIdAndWorkspaceIdAndDeletedAtIsNull(documentId, workspaceId)
				.orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

		List<DocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
		if (chunks.isEmpty()) {
			throw new BadRequestException("No chunks found. Call /process before /embed.");
		}
		if (document.getStatus() == DocumentStatus.UPLOADED) {
			throw new BadRequestException("Document must be processed (chunked) before embedding. Call /process first.");
		}

		int chunkCount = chunks.size();
		boolean notify = IngestEta.shouldNotify(
				chunkCount,
				embedSpeedTracker.averageMs()
		);
		ingestProgress.markEmbedding(documentId, chunkCount, notify);
		document.setStatus(DocumentStatus.EMBEDDING);
		document.setFailureReason(null);
		document.setChunkCount(chunkCount);
		if (notify) {
			document.setNotifyOnReady(true);
		}

		try {
			int embeddedNow = 0;
			int skipped = 0;
			int done = 0;
			for (DocumentChunk chunk : chunks) {
				if (vectorStore.hasEmbedding(chunk.getId())) {
					skipped++;
				}
				else {
					long started = System.nanoTime();
					float[] vector = embedWithRetry(chunk.getContent());
					vectorStore.saveEmbedding(chunk.getId(), vector, embeddingClient.modelId());
					embedSpeedTracker.record((System.nanoTime() - started) / 1_000_000L);
					embeddedNow++;
				}
				done++;
				ingestProgress.setEmbeddedCount(documentId, done);
			}
			document.setEmbeddedCount(done);

			if (searchIndexPurge.discardChunksIfNotSearchable(workspaceId, documentId)) {
				throw new ResourceNotFoundException("Document not found: " + documentId);
			}

			int markedReady = ingestProgress.markReady(documentId);
			if (markedReady == 0) {
				chunkRepository.deleteByDocumentId(documentId);
				throw new ResourceNotFoundException("Document not found: " + documentId);
			}
			document.setStatus(DocumentStatus.READY);
			document.setFailureReason(null);

			long totalEmbedded = vectorStore.countEmbeddedChunks(documentId);
			log.info(
					"Document {} embeddings: created={}, skipped={}, totalEmbedded={}, model={} → READY",
					documentId,
					embeddedNow,
					skipped,
					totalEmbedded,
					embeddingClient.modelId()
			);

			return new DocumentEmbedResponse(
					documentMapper.toResponse(document),
					embeddedNow,
					skipped,
					totalEmbedded,
					embeddingClient.modelId()
			);
		}
		catch (RuntimeException ex) {
			int saved = (int) vectorStore.countEmbeddedChunks(documentId);
			String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
			if (saved > 0 && chunkCount > 0) {
				reason = "Failed after " + saved + " of " + chunkCount
						+ " chunks. Retry starts from the beginning. " + reason;
			}
			if (reason.length() > 900) {
				reason = reason.substring(0, 900);
			}
			ingestProgress.markFailed(documentId, reason);
			document.setStatus(DocumentStatus.FAILED);
			document.setFailureReason(reason);
			throw ex;
		}
	}

	/**
	 * One chunk. Retries transient provider errors; already-saved chunks are never undone.
	 */
	float[] embedWithRetry(String content) {
		RuntimeException last = null;
		for (int attempt = 1; attempt <= EMBED_ATTEMPTS; attempt++) {
			try {
				return embeddingClient.embed(content, EmbeddingTask.DOCUMENT);
			}
			catch (RuntimeException ex) {
				last = ex;
				log.warn(
						"Embed attempt {}/{} failed: {}",
						attempt,
						EMBED_ATTEMPTS,
						ex.getMessage()
				);
				if (attempt < EMBED_ATTEMPTS) {
					sleepQuietly(400L * attempt);
				}
			}
		}
		throw last;
	}

	private static void sleepQuietly(long ms) {
		try {
			Thread.sleep(ms);
		}
		catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	@Transactional(readOnly = true)
	public List<SearchHitResponse> search(UUID workspaceId, String query, int topK) {
		workspaceService.requireOwnedWorkspace(workspaceId);

		if (query == null || query.isBlank()) {
			throw new BadRequestException("query is required");
		}
		int k = topK <= 0 ? 5 : Math.min(topK, 50);
		return hybridRetriever.retrieve(workspaceId, List.of(query.trim()), k);
	}

	/**
	 * Multi-query + hybrid retrieval, fused with Reciprocal Rank Fusion.
	 * <p>
	 * Each query is searched with dense ANN and (when enabled) keyword/FTS.
	 * Ranked lists are fused so a follow-up like "complexity for Array, Stack, Queue…"
	 * can pull chunks for each topic, including exact tokens vectors often miss.
	 */
	@Transactional(readOnly = true)
	public List<SearchHitResponse> searchMulti(UUID workspaceId, List<String> queries, int topK) {
		workspaceService.requireOwnedWorkspace(workspaceId);

		if (queries == null || queries.isEmpty()) {
			throw new BadRequestException("queries are required");
		}
		List<String> cleaned = queries.stream()
				.filter(q -> q != null && !q.isBlank())
				.map(String::trim)
				.distinct()
				.toList();
		if (cleaned.isEmpty()) {
			throw new BadRequestException("queries are required");
		}

		int k = topK <= 0 ? 5 : Math.min(topK, 50);
		return hybridRetriever.retrieve(workspaceId, cleaned, k);
	}
}
