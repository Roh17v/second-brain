package com.secondbrain.search;

import java.util.List;
import java.util.UUID;

/**
 * Persistence + similarity search for chunk embeddings (pgvector).
 */
public interface VectorStore {

	void ensureSchema(int dimensions);

	void saveEmbedding(UUID chunkId, float[] embedding, String modelId);

	boolean hasEmbedding(UUID chunkId);

	long countEmbeddedChunks(UUID documentId);

	List<ScoredChunk> similaritySearch(UUID workspaceId, float[] queryEmbedding, int topK);
}
