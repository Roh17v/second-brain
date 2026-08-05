package com.secondbrain.ai.llm;

import java.util.List;
import java.util.function.Consumer;

/**
 * Port for chat/completion providers. Implementations: Ollama, Gemini, echo (tests), …
 * <p>
 * Chat/RAG code depends only on this interface — swap providers via config.
 */
public interface LlmClient {

	/**
	 * Non-streaming completion.
	 */
	String chat(String systemPrompt, List<LlmMessage> messages);

	/**
	 * Streams text deltas. Default: one-shot {@link #chat} then a single callback.
	 * Providers with native streaming should override.
	 */
	default void streamChat(String systemPrompt, List<LlmMessage> messages, Consumer<String> onToken) {
		String full = chat(systemPrompt, messages);
		if (full != null && !full.isEmpty()) {
			onToken.accept(full);
		}
	}

	/**
	 * Model id reported to clients / logs.
	 */
	String modelId();
}
