package com.secondbrain.ai.embedding;

import java.util.List;

/**
 * Abstraction over embedding providers (Ollama, OpenAI, local models, etc.).
 */
public interface EmbeddingClient {

	/**
	 * Creates an embedding vector for a single text.
	 */
	float[] embed(String text);

	/**
	 * Batch helper. Default implementation embeds sequentially.
	 */
	default List<float[]> embedAll(List<String> texts) {
		return texts.stream().map(this::embed).toList();
	}

	int dimensions();

	String modelId();
}
