package com.secondbrain.ai.llm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondbrain.ai.AiProviders;
import com.secondbrain.common.exception.BadRequestException;

/**
 * Google Gemini chat via AI Studio / Gemini API ({@code generateContent} + SSE stream).
 * Active when {@code app.llm.provider=gemini}.
 */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = AiProviders.LLM_GEMINI)
public class GeminiLlmClient implements LlmClient {

	private static final String DEFAULT_BASE = "https://generativelanguage.googleapis.com";

	private final LlmProperties properties;
	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	public GeminiLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		var factory = new JdkClientHttpRequestFactory();
		factory.setReadTimeout(java.time.Duration.ofSeconds(Math.max(30, properties.getTimeoutSeconds())));
		this.restClient = RestClient.builder()
				.baseUrl(normalizeBase(properties.getBaseUrl()))
				.requestFactory(factory)
				.build();
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(15))
				.build();
	}

	@Override
	public String chat(String systemPrompt, List<LlmMessage> messages) {
		requireApiKey();
		String model = stripModelsPrefix(properties.getModel());
		Map<String, Object> body = buildBody(systemPrompt, messages);

		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> response = restClient.post()
					.uri(uriBuilder -> uriBuilder
							.path("/v1beta/models/{model}:generateContent")
							.queryParam("key", properties.getApiKey().trim())
							.build(model))
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(Map.class);

			String text = extractText(response);
			if (text == null || text.isBlank()) {
				throw new BadRequestException("Gemini returned an empty answer");
			}
			return text.trim();
		}
		catch (RestClientResponseException ex) {
			throw new BadRequestException(
					"Gemini chat failed (" + ex.getStatusCode().value() + "): "
							+ truncate(ex.getResponseBodyAsString())
			);
		}
		catch (RestClientException ex) {
			throw new BadRequestException("Gemini chat request failed: " + ex.getMessage());
		}
	}

	@Override
	public void streamChat(String systemPrompt, List<LlmMessage> messages, Consumer<String> onToken) {
		requireApiKey();
		String model = stripModelsPrefix(properties.getModel());
		try {
			String json = objectMapper.writeValueAsString(buildBody(systemPrompt, messages));
			String key = URLEncoder.encode(properties.getApiKey().trim(), StandardCharsets.UTF_8);
			String url = normalizeBase(properties.getBaseUrl())
					+ "/v1beta/models/"
					+ model
					+ ":streamGenerateContent?alt=sse&key="
					+ key;

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.timeout(Duration.ofSeconds(Math.max(60, properties.getTimeoutSeconds())))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(json))
					.build();

			HttpResponse<java.io.InputStream> response = httpClient.send(
					request,
					HttpResponse.BodyHandlers.ofInputStream()
			);

			if (response.statusCode() >= 400) {
				String err = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
				throw new BadRequestException(
						"Gemini stream failed (HTTP " + response.statusCode() + "): " + truncate(err)
				);
			}

			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(response.body(), StandardCharsets.UTF_8)
			)) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (!line.startsWith("data:")) {
						continue;
					}
					String data = line.substring(5).trim();
					if (data.isEmpty() || "[DONE]".equals(data)) {
						continue;
					}
					JsonNode node = objectMapper.readTree(data);
					String delta = extractDelta(node);
					if (delta != null && !delta.isEmpty()) {
						onToken.accept(delta);
					}
				}
			}
		}
		catch (BadRequestException ex) {
			throw ex;
		}
		catch (IOException | InterruptedException ex) {
			if (ex instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new BadRequestException("Gemini stream error: " + ex.getMessage());
		}
	}

	@Override
	public String modelId() {
		return stripModelsPrefix(properties.getModel());
	}

	private Map<String, Object> buildBody(String systemPrompt, List<LlmMessage> messages) {
		Map<String, Object> body = new LinkedHashMap<>();
		if (systemPrompt != null && !systemPrompt.isBlank()) {
			body.put("system_instruction", Map.of(
					"parts", List.of(Map.of("text", systemPrompt))
			));
		}

		List<Map<String, Object>> contents = new ArrayList<>();
		for (LlmMessage message : messages) {
			String role = mapRole(message.role());
			contents.add(Map.of(
					"role", role,
					"parts", List.of(Map.of("text", message.content() == null ? "" : message.content()))
			));
		}
		body.put("contents", contents);
		body.put("generationConfig", Map.of(
				"temperature", properties.getTemperature()
		));
		return body;
	}

	private static String mapRole(String role) {
		if (role == null) {
			return "user";
		}
		return switch (role.toLowerCase()) {
			case "assistant", "model" -> "model";
			case "system" -> "user"; // system goes in system_instruction; fallback
			default -> "user";
		};
	}

	@SuppressWarnings("unchecked")
	private static String extractText(Map<String, Object> response) {
		if (response == null) {
			return null;
		}
		Object candidates = response.get("candidates");
		if (!(candidates instanceof List<?> list) || list.isEmpty()) {
			return null;
		}
		Object first = list.getFirst();
		if (!(first instanceof Map<?, ?> cand)) {
			return null;
		}
		Object content = cand.get("content");
		if (!(content instanceof Map<?, ?> contentMap)) {
			return null;
		}
		Object parts = contentMap.get("parts");
		if (!(parts instanceof List<?> partList) || partList.isEmpty()) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (Object p : partList) {
			if (p instanceof Map<?, ?> partMap && partMap.get("text") != null) {
				sb.append(partMap.get("text").toString());
			}
		}
		return sb.toString();
	}

	private static String extractDelta(JsonNode node) {
		JsonNode parts = node.path("candidates").path(0).path("content").path("parts");
		if (!parts.isArray() || parts.isEmpty()) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (JsonNode part : parts) {
			if (part.hasNonNull("text")) {
				sb.append(part.get("text").asText());
			}
		}
		return sb.toString();
	}

	private void requireApiKey() {
		if (!properties.hasApiKey()) {
			throw new BadRequestException(
					"Gemini chat requires an API key. Set GEMINI_API_KEY or LLM_API_KEY."
			);
		}
	}

	private static String normalizeBase(String baseUrl) {
		if (baseUrl == null || baseUrl.isBlank() || baseUrl.contains("localhost") || baseUrl.contains("11434")) {
			return DEFAULT_BASE;
		}
		return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	private static String stripModelsPrefix(String model) {
		if (model == null || model.isBlank()) {
			return "gemini-2.5-flash";
		}
		String m = model.trim();
		if (m.startsWith("models/")) {
			return m.substring("models/".length());
		}
		return m;
	}

	private static String truncate(String s) {
		if (s == null || s.isBlank()) {
			return "(empty)";
		}
		String t = s.replaceAll("\\s+", " ").trim();
		return t.length() > 400 ? t.substring(0, 400) + "…" : t;
	}
}
