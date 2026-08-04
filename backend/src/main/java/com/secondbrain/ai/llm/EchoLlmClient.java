package com.secondbrain.ai.llm;

import java.util.List;
import java.util.function.Consumer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic offline LLM for tests (supports streaming token simulation).
 */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "echo")
public class EchoLlmClient implements LlmClient {

	@Override
	public String chat(String systemPrompt, List<LlmMessage> messages) {
		String lastUser = messages.stream()
				.filter(m -> "user".equalsIgnoreCase(m.role()))
				.reduce((a, b) -> b)
				.map(LlmMessage::content)
				.orElse("");
		return "Echo answer based on retrieved context for: " + lastUser
				+ (systemPrompt != null && systemPrompt.contains("[1]")
				? " Sources were provided."
				: " No sources.");
	}

	@Override
	public void streamChat(String systemPrompt, List<LlmMessage> messages, Consumer<String> onToken) {
		String full = chat(systemPrompt, messages);
		for (String part : full.split("(?<=\\s)")) {
			if (!part.isEmpty()) {
				onToken.accept(part);
			}
		}
	}

	@Override
	public String modelId() {
		return "echo-llm";
	}
}
