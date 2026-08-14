package com.secondbrain.search;

import java.util.List;
import java.util.UUID;

/**
 * Persistence + retrieval for chunk embeddings and keyword search.
 * Implementations must only return chunks from non-deleted {@code READY} documents.
 */
public interface VectorStore {

	void ensureSchema(int dimensions);

	void saveEmbedding(UUID chunkId, float[] embedding, String modelId);

	boolean hasEmbedding(UUID chunkId);

	long countEmbeddedChunks(UUID documentId);

	List<ScoredChunk> similaritySearch(UUID workspaceId, float[] queryEmbedding, int topK);

	/**
	 * Sparse / full-text ranking for a workspace (Postgres FTS in prod, lexical in tests).
	 * Empty or unusable queries return an empty list — never fail the dense path.
	 */
	List<ScoredChunk> keywordSearch(UUID workspaceId, String query, int topK);
}
