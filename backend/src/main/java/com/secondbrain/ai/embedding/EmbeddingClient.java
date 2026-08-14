package com.secondbrain.ai.embedding;

import java.util.List;

/**
 * Port for embedding providers. Implementations: Ollama, Gemini, hashing (tests), …
 * <p>
 * Callers must not assume Ollama or any HTTP shape — only this contract.
 * When switching models/providers, re-embed all document chunks (vector spaces differ).
 */
public interface EmbeddingClient {

	/**
	 * Creates an embedding vector for a single text (chunk or query).
	 * Defaults to {@link EmbeddingTask#DOCUMENT}.
	 */
	float[] embed(String text);

	/**
	 * Same as {@link #embed(String)} with an explicit task (query vs document).
	 */
	default float[] embed(String text, EmbeddingTask task) {
		return embed(text);
	}

	/**
	 * Batch helper. Default embeds sequentially; providers may override for bulk APIs.
	 */
	default List<float[]> embedAll(List<String> texts) {
		return texts.stream().map(this::embed).toList();
	}

	/**
	 * Declared vector size; must match {@code app.embedding.dimensions} and DB column.
	 */
	int dimensions();

	/**
	 * Stable model id stored with vectors for audit / mismatch detection.
	 */
	String modelId();
}
