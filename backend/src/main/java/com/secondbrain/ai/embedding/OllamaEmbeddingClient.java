package com.secondbrain.ai.embedding;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.secondbrain.ai.AiProviders;
import com.secondbrain.common.exception.BadRequestException;

/**
 * Ollama {@link EmbeddingClient} — local models via {@code POST /api/embeddings}.
 * Active when {@code app.embedding.provider=ollama}.
 */
@Component
@ConditionalOnProperty(name = "app.embedding.provider", havingValue = AiProviders.EMBEDDING_OLLAMA, matchIfMissing = true)
public class OllamaEmbeddingClient implements EmbeddingClient {

	private final EmbeddingProperties properties;
	private final RestClient restClient;

	public OllamaEmbeddingClient(EmbeddingProperties properties) {
		this.properties = properties;
		this.restClient = RestClient.builder()
				.baseUrl(properties.getBaseUrl())
				.build();
	}

	@Override
	public float[] embed(String text) {
		if (text == null || text.isBlank()) {
			throw new BadRequestException("Cannot embed empty text");
		}

		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> body = restClient.post()
					.uri("/api/embeddings")
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of(
							"model", properties.getModel(),
							"prompt", text
					))
					.retrieve()
					.body(Map.class);

			if (body == null || !body.containsKey("embedding")) {
				throw new BadRequestException("Ollama embedding response missing 'embedding' field");
			}

			@SuppressWarnings("unchecked")
			List<Number> values = (List<Number>) body.get("embedding");
			float[] vector = new float[values.size()];
			for (int i = 0; i < values.size(); i++) {
				vector[i] = values.get(i).floatValue();
			}

			if (vector.length != properties.getDimensions()) {
				throw new BadRequestException(
						"Embedding dimension mismatch: expected "
								+ properties.getDimensions()
								+ " but model returned "
								+ vector.length
								+ ". Update app.embedding.dimensions to match the model."
				);
			}
			return vector;
		}
		catch (RestClientException ex) {
			throw new BadRequestException(
					"Failed to call Ollama embeddings at "
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
	public int dimensions() {
		return properties.getDimensions();
	}

	@Override
	public String modelId() {
		return properties.getModel();
	}
}
