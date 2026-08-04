package com.secondbrain.ai.llm;

import java.util.List;

/**
 * Abstraction over chat/completion LLM providers.
 */
public interface LlmClient {

	/**
	 * Runs a non-streaming chat completion.
	 *
	 * @param systemPrompt instructions / RAG rules
	 * @param messages     prior turns + current user message (roles: system|user|assistant)
	 */
	String chat(String systemPrompt, List<LlmMessage> messages);

	String modelId();
}
