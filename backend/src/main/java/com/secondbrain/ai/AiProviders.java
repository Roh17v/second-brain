package com.secondbrain.ai;

/**
 * Canonical provider ids for {@code app.embedding.provider}, {@code app.llm.provider},
 * and {@code app.ocr.provider}. Use these strings in {@code @ConditionalOnProperty}.
 */
public final class AiProviders {

	private AiProviders() {
	}

	// --- embeddings ---
	public static final String EMBEDDING_OLLAMA = "ollama";
	public static final String EMBEDDING_GEMINI = "gemini";
	public static final String EMBEDDING_HASHING = "hashing";

	// --- chat LLM ---
	public static final String LLM_OLLAMA = "ollama";
	public static final String LLM_GEMINI = "gemini";
	/** Groq OpenAI-compatible API (https://api.groq.com/openai/v1) */
	public static final String LLM_GROQ = "groq";
	public static final String LLM_ECHO = "echo";

	// --- OCR ---
	public static final String OCR_NONE = "none";
	public static final String OCR_MISTRAL = "mistral";
}
