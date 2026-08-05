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
 * Groq chat via OpenAI-compatible API
 * ({@code POST /openai/v1/chat/completions}).
 * <p>
 * Active when {@code app.llm.provider=groq}.
 * Set {@code GROQ_API_KEY} and {@code LLM_MODEL} to the exact model id from
 * the Groq console (e.g. a Qwen 3.x variant).
 *
 * @see <a href="https://console.groq.com/docs/models">Groq models</a>
 */
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = AiProviders.LLM_GROQ)
public class GroqLlmClient implements LlmClient {

	private static final String DEFAULT_BASE = "https://api.groq.com/openai/v1";

	private final LlmProperties properties;
	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	public GroqLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
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
		Map<String, Object> body = buildBody(systemPrompt, messages, false);

		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> response = restClient.post()
					.uri("/chat/completions")
					.contentType(MediaType.APPLICATION_JSON)
					.header("Authorization", "Bearer " + properties.getApiKey().trim())
					.body(body)
					.retrieve()
					.body(Map.class);

			String text = extractMessageContent(response);
			if (text == null || text.isBlank()) {
				throw new BadRequestException("Groq returned an empty answer");
			}
			return ThinkingStreamFilter.stripComplete(text);
		}
		catch (RestClientResponseException ex) {
			throw new BadRequestException(
					"Groq chat failed (" + ex.getStatusCode().value() + "): "
							+ truncate(ex.getResponseBodyAsString())
			);
		}
		catch (RestClientException ex) {
			throw new BadRequestException("Groq chat request failed: " + ex.getMessage());
		}
	}

	@Override
	public void streamChat(String systemPrompt, List<LlmMessage> messages, Consumer<String> onToken) {
		requireApiKey();
		try {
			String json = objectMapper.writeValueAsString(buildBody(systemPrompt, messages, true));
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(normalizeBase(properties.getBaseUrl()) + "/chat/completions"))
					.timeout(Duration.ofSeconds(Math.max(60, properties.getTimeoutSeconds())))
					.header("Content-Type", "application/json")
					.header("Authorization", "Bearer " + properties.getApiKey().trim())
					.POST(HttpRequest.BodyPublishers.ofString(json))
					.build();

			HttpResponse<java.io.InputStream> response = httpClient.send(
					request,
					HttpResponse.BodyHandlers.ofInputStream()
			);

			if (response.statusCode() >= 400) {
				String err = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
				throw new BadRequestException(
						"Groq stream failed (HTTP " + response.statusCode() + "): " + truncate(err)
				);
			}

			ThinkingStreamFilter thinkFilter = new ThinkingStreamFilter();
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
					String delta = extractStreamDelta(node);
					if (delta != null && !delta.isEmpty()) {
						String safe = thinkFilter.accept(delta);
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
		catch (BadRequestException ex) {
			throw ex;
		}
		catch (IOException | InterruptedException ex) {
			if (ex instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new BadRequestException("Groq stream error: " + ex.getMessage());
		}
	}

	@Override
	public String modelId() {
		return properties.getModel();
	}

	private Map<String, Object> buildBody(String systemPrompt, List<LlmMessage> messages, boolean stream) {
		List<Map<String, String>> payload = new ArrayList<>();
		if (systemPrompt != null && !systemPrompt.isBlank()) {
			payload.add(Map.of("role", "system", "content", systemPrompt));
		}
		for (LlmMessage message : messages) {
			payload.add(Map.of(
					"role", mapRole(message.role()),
					"content", message.content() == null ? "" : message.content()
			));
		}

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", properties.getModel());
		body.put("messages", payload);
		body.put("temperature", properties.getTemperature());
		body.put("stream", stream);
		// Qwen 3.6 on Groq: "none" disables reasoning tokens in the stream
		String effort = properties.getReasoningEffort();
		if (effort != null && !effort.isBlank()) {
			body.put("reasoning_effort", effort.trim());
		}
		return body;
	}

	private static String mapRole(String role) {
		if (role == null) {
			return "user";
		}
		return switch (role.toLowerCase()) {
			case "assistant", "model" -> "assistant";
			case "system" -> "system";
			default -> "user";
		};
	}

	@SuppressWarnings("unchecked")
	private static String extractMessageContent(Map<String, Object> response) {
		if (response == null) {
			return null;
		}
		Object choices = response.get("choices");
		if (!(choices instanceof List<?> list) || list.isEmpty()) {
			return null;
		}
		Object first = list.getFirst();
		if (!(first instanceof Map<?, ?> choice)) {
			return null;
		}
		Object message = choice.get("message");
		if (!(message instanceof Map<?, ?> msg)) {
			return null;
		}
		Object content = msg.get("content");
		return content == null ? null : content.toString();
	}

	private static String extractStreamDelta(JsonNode node) {
		JsonNode delta = node.path("choices").path(0).path("delta").path("content");
		if (delta.isMissingNode() || delta.isNull()) {
			return null;
		}
		return delta.asText();
	}

	private void requireApiKey() {
		if (!properties.hasApiKey()) {
			throw new BadRequestException(
					"Groq requires an API key. Set GROQ_API_KEY or LLM_API_KEY."
			);
		}
	}

	private static String normalizeBase(String baseUrl) {
		if (baseUrl == null || baseUrl.isBlank()
				|| baseUrl.contains("localhost")
				|| baseUrl.contains("11434")
				|| baseUrl.contains("googleapis.com")) {
			return DEFAULT_BASE;
		}
		String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		// Accept either https://api.groq.com or .../openai/v1
		if (base.endsWith("/openai/v1")) {
			return base;
		}
		if (base.contains("api.groq.com") && !base.contains("/openai")) {
			return base + "/openai/v1";
		}
		return base;
	}

	private static String truncate(String s) {
		if (s == null || s.isBlank()) {
			return "(empty)";
		}
		String t = s.replaceAll("\\s+", " ").trim();
		return t.length() > 400 ? t.substring(0, 400) + "…" : t;
	}
}
