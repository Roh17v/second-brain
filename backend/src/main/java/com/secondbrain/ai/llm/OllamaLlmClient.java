package com.secondbrain.ai.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.secondbrain.common.exception.BadRequestException;

/**
 * Ollama chat API client ({@code POST /api/chat}, non-streaming).
 */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaLlmClient implements LlmClient {

	private final LlmProperties properties;
	private final RestClient restClient;

	public OllamaLlmClient(LlmProperties properties) {
		this.properties = properties;
		this.restClient = RestClient.builder()
				.baseUrl(properties.getBaseUrl())
				.build();
	}

	@Override
	public String chat(String systemPrompt, List<LlmMessage> messages) {
		List<Map<String, String>> payloadMessages = new ArrayList<>();
		if (systemPrompt != null && !systemPrompt.isBlank()) {
			payloadMessages.add(Map.of("role", "system", "content", systemPrompt));
		}
		for (LlmMessage message : messages) {
			payloadMessages.add(Map.of("role", message.role(), "content", message.content()));
		}

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", properties.getModel());
		body.put("stream", false);
		body.put("messages", payloadMessages);
		body.put("options", Map.of("temperature", properties.getTemperature()));

		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> response = restClient.post()
					.uri("/api/chat")
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(Map.class);

			if (response == null || !response.containsKey("message")) {
				throw new BadRequestException("Ollama chat response missing message");
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> message = (Map<String, Object>) response.get("message");
			Object content = message.get("content");
			if (content == null || content.toString().isBlank()) {
				throw new BadRequestException("Ollama returned an empty answer");
			}
			return content.toString().trim();
		}
		catch (RestClientException ex) {
			throw new BadRequestException(
					"Failed to call Ollama chat at "
							+ properties.getBaseUrl()
							+ ". Is Ollama running and is model '"
							+ properties.getModel()
							+ "' pulled? ("
							+ ex.getMessage()
							+ ")"
			);
		}
	}

	@Override
	public String modelId() {
		return properties.getModel();
	}
}
