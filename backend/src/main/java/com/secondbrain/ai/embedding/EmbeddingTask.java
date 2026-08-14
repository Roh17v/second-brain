package com.secondbrain.ai.embedding;

/**
 * How the text will be used. Some models (nomic, Gemini) need different
 * prefixes or task types for chunks vs search queries.
 */
public enum EmbeddingTask {
	DOCUMENT,
	QUERY
}
