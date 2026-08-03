package com.secondbrain.search;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secondbrain.ai.embedding.EmbeddingClient;
import com.secondbrain.common.exception.BadRequestException;
import com.secondbrain.common.exception.ResourceNotFoundException;
import com.secondbrain.document.dto.DocumentResponse;
import com.secondbrain.document.entity.Document;
import com.secondbrain.document.entity.DocumentChunk;
import com.secondbrain.document.entity.DocumentStatus;
import com.secondbrain.document.mapper.DocumentMapper;
import com.secondbrain.document.repository.DocumentChunkRepository;
import com.secondbrain.document.repository.DocumentRepository;
import com.secondbrain.workspace.service.WorkspaceService;

import jakarta.annotation.PostConstruct;

@Service
public class EmbeddingService {

	private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

	private final EmbeddingClient embeddingClient;
	private final VectorStore vectorStore;
	private final DocumentRepository documentRepository;
	private final DocumentChunkRepository chunkRepository;
	private final DocumentMapper documentMapper;
	private final WorkspaceService workspaceService;

	public EmbeddingService(
			EmbeddingClient embeddingClient,
			VectorStore vectorStore,
			DocumentRepository documentRepository,
			DocumentChunkRepository chunkRepository,
			DocumentMapper documentMapper,
			WorkspaceService workspaceService
	) {
		this.embeddingClient = embeddingClient;
		this.vectorStore = vectorStore;
		this.documentRepository = documentRepository;
		this.chunkRepository = chunkRepository;
		this.documentMapper = documentMapper;
		this.workspaceService = workspaceService;
	}

	@PostConstruct
	void initSchema() {
		vectorStore.ensureSchema(embeddingClient.dimensions());
	}

	/**
	 * Embeds all chunks for a document that do not yet have embeddings.
	 */
	@Transactional
	public DocumentEmbedResponse embedDocument(UUID workspaceId, UUID documentId) {
		workspaceService.requireOwnedWorkspace(workspaceId);

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

		int embeddedNow = 0;
		int skipped = 0;
		for (DocumentChunk chunk : chunks) {
			if (vectorStore.hasEmbedding(chunk.getId())) {
				skipped++;
				continue;
			}
			float[] vector = embeddingClient.embed(chunk.getContent());
			vectorStore.saveEmbedding(chunk.getId(), vector, embeddingClient.modelId());
			embeddedNow++;
		}

		long totalEmbedded = vectorStore.countEmbeddedChunks(documentId);
		log.info(
				"Document {} embeddings: created={}, skipped={}, totalEmbedded={}, model={}",
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

	@Transactional(readOnly = true)
	public List<SearchHitResponse> search(UUID workspaceId, String query, int topK) {
		workspaceService.requireOwnedWorkspace(workspaceId);

		if (query == null || query.isBlank()) {
			throw new BadRequestException("query is required");
		}
		int k = topK <= 0 ? 5 : Math.min(topK, 50);

		float[] queryVector = embeddingClient.embed(query.trim());
		List<ScoredChunk> scored = vectorStore.similaritySearch(workspaceId, queryVector, k);

		return scored.stream()
				.map(c -> new SearchHitResponse(
						c.chunkId(),
						c.documentId(),
						c.chunkIndex(),
						c.content(),
						c.score()
				))
				.toList();
	}
}
