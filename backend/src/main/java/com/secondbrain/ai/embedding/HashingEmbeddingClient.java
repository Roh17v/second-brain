package com.secondbrain.ai.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic offline embeddings for tests. Not for production retrieval quality.
 */
@Component
@ConditionalOnProperty(name = "app.embedding.provider", havingValue = com.secondbrain.ai.AiProviders.EMBEDDING_HASHING)
public class HashingEmbeddingClient implements EmbeddingClient {

	private final EmbeddingProperties properties;

	public HashingEmbeddingClient(EmbeddingProperties properties) {
		this.properties = properties;
	}

	@Override
	public float[] embed(String text) {
		String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
		byte[] hash = sha256(normalized);
		float[] vector = new float[properties.getDimensions()];
		for (int i = 0; i < vector.length; i++) {
			int b = hash[i % hash.length] & 0xff;
			vector[i] = (b / 255.0f) * 2.0f - 1.0f;
		}
		// L2 normalize for cosine-friendly comparisons
		float norm = 0f;
		for (float v : vector) {
			norm += v * v;
		}
		norm = (float) Math.sqrt(norm);
		if (norm > 0) {
			for (int i = 0; i < vector.length; i++) {
				vector[i] /= norm;
			}
		}
		return vector;
	}

	@Override
	public int dimensions() {
		return properties.getDimensions();
	}

	@Override
	public String modelId() {
		return "hashing-embed-" + properties.getDimensions();
	}

	private static byte[] sha256(String text) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
