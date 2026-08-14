package com.secondbrain.search;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.secondbrain.ai.embedding.EmbeddingClient;
import com.secondbrain.ai.embedding.EmbeddingTask;
import com.secondbrain.search.rerank.ChunkReranker;

/**
 * Retrieve a wide hybrid candidate pool, then rerank to the prompt top-K.
 * Does not apply auth — callers must already have checked workspace ownership.
 */
@Component
public class HybridRetriever {

	private static final Logger log = LoggerFactory.getLogger(HybridRetriever.class);

	private final EmbeddingClient embeddingClient;
	private final VectorStore vectorStore;
	private final SearchProperties searchProperties;
	private final ChunkReranker chunkReranker;

	public HybridRetriever(
			EmbeddingClient embeddingClient,
			VectorStore vectorStore,
			SearchProperties searchProperties,
			ChunkReranker chunkReranker
	) {
		this.embeddingClient = embeddingClient;
		this.vectorStore = vectorStore;
		this.searchProperties = searchProperties;
		this.chunkReranker = chunkReranker;
	}

	public List<SearchHitResponse> retrieve(UUID workspaceId, List<String> queries, int topK) {
		List<String> cleaned = queries == null
				? List.of()
				: queries.stream()
						.filter(q -> q != null && !q.isBlank())
						.map(String::trim)
						.distinct()
						.toList();
		if (cleaned.isEmpty()) {
			return List.of();
		}

		int k = topK <= 0 ? 5 : Math.min(topK, 50);
		int candidateK = searchProperties.candidateLimit(k);
		int finalK = searchProperties.finalTopK(k, cleaned.size());
		boolean hybrid = searchProperties.isHybridEnabled();

		List<List<SearchHitResponse>> allLists = new ArrayList<>();
		List<List<SearchHitResponse>> perQuery = new ArrayList<>();
		int keywordLists = 0;
		for (String q : cleaned) {
			List<List<SearchHitResponse>> thisQuery = new ArrayList<>();
			float[] queryVector = embeddingClient.embed(q, EmbeddingTask.QUERY);
			List<SearchHitResponse> dense =
					toHits(vectorStore.similaritySearch(workspaceId, queryVector, candidateK));
			thisQuery.add(dense);
			allLists.add(dense);
			if (hybrid) {
				try {
					List<SearchHitResponse> keywordHits =
							toHits(vectorStore.keywordSearch(workspaceId, q, candidateK));
					thisQuery.add(keywordHits);
					allLists.add(keywordHits);
					if (!keywordHits.isEmpty()) {
						keywordLists++;
					}
				}
				catch (RuntimeException ex) {
					log.warn("Keyword search failed; continuing with dense results: {}", ex.getMessage());
				}
			}
			perQuery.add(thisQuery.size() == 1
					? thisQuery.getFirst()
					: ReciprocalRankFusion.fuse(thisQuery, candidateK));
		}

		List<SearchHitResponse> fused = allLists.size() == 1
				? allLists.getFirst().stream().limit(candidateK).toList()
				: ReciprocalRankFusion.fuse(allLists, candidateK);

		List<SearchHitResponse> pool;
		if (cleaned.size() > 1) {
			pool = QueryCoveragePacker.pack(cleaned, perQuery, fused, candidateK);
		}
		else {
			pool = fused;
		}

		List<SearchHitResponse> ranked = (!hybrid && cleaned.size() == 1 && !searchProperties.isRerankEnabled())
				? pool.stream().limit(finalK).toList()
				: chunkReranker.rerank(cleaned, pool, finalK);

		log.info(
				"RAG hybrid workspace={} hybrid={} rerank={} queries={} candidateK={} lists={} keywordNonEmpty={} pool={} returned={}",
				workspaceId,
				hybrid,
				chunkReranker.modelId(),
				cleaned.size(),
				candidateK,
				allLists.size(),
				keywordLists,
				pool.size(),
				ranked.size()
		);
		return ranked;
	}

	static List<SearchHitResponse> toHits(List<ScoredChunk> scored) {
		if (scored == null || scored.isEmpty()) {
			return List.of();
		}
		List<SearchHitResponse> hits = new ArrayList<>(scored.size());
		for (ScoredChunk c : scored) {
			hits.add(new SearchHitResponse(
					c.chunkId(),
					c.documentId(),
					c.chunkIndex(),
					c.content(),
					c.score()
			));
		}
		return hits;
	}
}
