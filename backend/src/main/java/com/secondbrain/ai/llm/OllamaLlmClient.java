package com.secondbrain.ai.llm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondbrain.ai.AiProviders;
import com.secondbrain.common.exception.BadRequestException;

/**
 * Ollama {@link LlmClient} — local chat via {@code POST /api/chat} (stream + non-stream).
 * Active when {@code app.llm.provider=ollama}.
 */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = AiProviders.LLM_OLLAMA, matchIfMissing = true)
public class OllamaLlmClient implements LlmClient {

	private final LlmProperties properties;
	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	public OllamaLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.restClient = RestClient.builder()
				.baseUrl(properties.getBaseUrl())
				.build();
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.build();
	}

	@Override
	public String chat(String systemPrompt, List<LlmMessage> messages) {
		Map<String, Object> body = buildBody(systemPrompt, messages, false);

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
			return ThinkingStreamFilter.stripComplete(content.toString());
		}
		catch (RestClientException ex) {
			throw ollamaError(ex.getMessage());
		}
	}

	@Override
	public void streamChat(String systemPrompt, List<LlmMessage> messages, Consumer<String> onToken) {
		Map<String, Object> body = buildBody(systemPrompt, messages, true);
		try {
			String json = objectMapper.writeValueAsString(body);
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(trimSlash(properties.getBaseUrl()) + "/api/chat"))
					.timeout(Duration.ofMinutes(10))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(json))
					.build();

			HttpResponse<java.io.InputStream> response = httpClient.send(
					request,
					HttpResponse.BodyHandlers.ofInputStream()
			);

			if (response.statusCode() >= 400) {
				String err = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
				throw ollamaError("HTTP " + response.statusCode() + " " + err);
			}

			ThinkingStreamFilter thinkFilter = new ThinkingStreamFilter();
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(response.body(), StandardCharsets.UTF_8)
			)) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.isBlank()) {
						continue;
					}
					JsonNode node = objectMapper.readTree(line);
					JsonNode contentNode = node.path("message").path("content");
					if (!contentNode.isMissingNode() && !contentNode.asText().isEmpty()) {
						String safe = thinkFilter.accept(contentNode.asText());
						if (!safe.isEmpty()) {
							onToken.accept(safe);
						}
					}
				}
			}
			String tail = thinkFilter.finish();
			if (!tail.isEmpty()) {
				onToken.accept(tail);
			}
		}
		catch (IOException | InterruptedException ex) {
			if (ex instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw ollamaError(ex.getMessage());
		}
	}

	@Override
	public String modelId() {
		return properties.getModel();
	}

	private Map<String, Object> buildBody(String systemPrompt, List<LlmMessage> messages, boolean stream) {
		List<Map<String, String>> payloadMessages = new ArrayList<>();
		if (systemPrompt != null && !systemPrompt.isBlank()) {
			payloadMessages.add(Map.of("role", "system", "content", systemPrompt));
		}
		for (LlmMessage message : messages) {
			payloadMessages.add(Map.of("role", message.role(), "content", message.content()));
		}

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", properties.getModel());
		body.put("stream", stream);
		body.put("messages", payloadMessages);
		body.put("options", Map.of("temperature", properties.getTemperature()));
		return body;
	}

	private BadRequestException ollamaError(String detail) {
		return new BadRequestException(
				"Failed to call Ollama chat at "
						+ properties.getBaseUrl()
						+ ". Is Ollama running and is model '"
						+ properties.getModel()
						+ "' pulled? ("
						+ detail
						+ ")"
		);
	}

	private static String trimSlash(String url) {
		if (url == null || url.isEmpty()) {
			return "";
		}
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}
