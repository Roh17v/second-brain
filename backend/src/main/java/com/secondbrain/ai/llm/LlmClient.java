package com.secondbrain.ai.llm;

import java.util.List;
import java.util.function.Consumer;

/**
 * Abstraction over chat/completion LLM providers.
 */
public interface LlmClient {

	/**
	 * Runs a non-streaming chat completion.
	 */
	String chat(String systemPrompt, List<LlmMessage> messages);

	/**
	 * Streams token deltas via {@code onToken}. Default implementation falls back to full chat.
	 */
	default void streamChat(String systemPrompt, List<LlmMessage> messages, Consumer<String> onToken) {
		String full = chat(systemPrompt, messages);
		if (full != null && !full.isEmpty()) {
			onToken.accept(full);
		}
	}

	String modelId();
}
