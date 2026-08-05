package com.secondbrain.ai.embedding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.secondbrain.ai.AiProviders;
import com.secondbrain.common.exception.BadRequestException;

/**
 * Google Gemini embeddings via AI Studio / Gemini API.
 * Active when {@code app.embedding.provider=gemini}.
 *
 * @see <a href="https://ai.google.dev/gemini-api/docs/embeddings">Embeddings docs</a>
 */
@Component
@ConditionalOnProperty(name = "app.embedding.provider", havingValue = AiProviders.EMBEDDING_GEMINI)
public class GeminiEmbeddingClient implements EmbeddingClient {

	private static final String DEFAULT_BASE = "https://generativelanguage.googleapis.com";

	private final EmbeddingProperties properties;
	private final RestClient restClient;

	public GeminiEmbeddingClient(EmbeddingProperties properties) {
		this.properties = properties;
		var factory = new JdkClientHttpRequestFactory();
		factory.setReadTimeout(java.time.Duration.ofSeconds(Math.max(15, properties.getTimeoutSeconds())));
		this.restClient = RestClient.builder()
				.baseUrl(normalizeBase(properties.getBaseUrl()))
				.requestFactory(factory)
				.build();
	}

	@Override
	public float[] embed(String text) {
		if (text == null || text.isBlank()) {
			throw new BadRequestException("Cannot embed empty text");
		}
		requireApiKey();

		String model = stripModelsPrefix(properties.getModel());
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("content", Map.of(
				"parts", List.of(Map.of("text", text))
		));
		// gemini-embedding-001 supports taskType; short text ≈ query, long ≈ document chunk
		if (model.contains("embedding-001")) {
			body.put("taskType", text.length() < 512 ? "RETRIEVAL_QUERY" : "RETRIEVAL_DOCUMENT");
		}
		int outDim = properties.getOutputDimensionality() > 0
				? properties.getOutputDimensionality()
				: properties.getDimensions();
		if (outDim > 0 && outDim != 3072) {
			body.put("outputDimensionality", outDim);
		}

		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> response = restClient.post()
					.uri(uriBuilder -> uriBuilder
							.path("/v1beta/models/{model}:embedContent")
							.queryParam("key", properties.getApiKey().trim())
							.build(model))
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(Map.class);

			float[] vector = extractValues(response);
			if (vector.length != properties.getDimensions()) {
				throw new BadRequestException(
						"Gemini embedding dimension mismatch: expected "
								+ properties.getDimensions()
								+ " but got "
								+ vector.length
								+ ". Set EMBEDDING_DIMENSIONS (and EMBEDDING_OUTPUT_DIMENSIONALITY) to match."
				);
			}
			return vector;
		}
		catch (RestClientResponseException ex) {
			throw new BadRequestException(
					"Gemini embed failed (" + ex.getStatusCode().value() + "): "
							+ truncate(ex.getResponseBodyAsString())
			);
		}
		catch (RestClientException ex) {
			throw new BadRequestException("Gemini embed request failed: " + ex.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	private static float[] extractValues(Map<String, Object> response) {
		if (response == null) {
			throw new BadRequestException("Gemini embed response was empty");
		}
		Object embedding = response.get("embedding");
		if (!(embedding instanceof Map<?, ?> embMap)) {
			throw new BadRequestException("Gemini embed response missing embedding.values");
		}
		Object valuesObj = embMap.get("values");
		if (!(valuesObj instanceof List<?> list) || list.isEmpty()) {
			throw new BadRequestException("Gemini embed response has no values");
		}
		float[] vector = new float[list.size()];
		for (int i = 0; i < list.size(); i++) {
			vector[i] = ((Number) list.get(i)).floatValue();
		}
		return vector;
	}

	private void requireApiKey() {
		if (!properties.hasApiKey()) {
			throw new BadRequestException(
					"Gemini embeddings require an API key. Set GEMINI_API_KEY or EMBEDDING_API_KEY."
			);
		}
	}

	@Override
	public int dimensions() {
		return properties.getDimensions();
	}

	@Override
	public String modelId() {
		return stripModelsPrefix(properties.getModel());
	}

	private static String normalizeBase(String baseUrl) {
		if (baseUrl == null || baseUrl.isBlank() || baseUrl.contains("localhost") || baseUrl.contains("11434")) {
			return DEFAULT_BASE;
		}
		return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	private static String stripModelsPrefix(String model) {
		if (model == null || model.isBlank()) {
			return "gemini-embedding-2";
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
