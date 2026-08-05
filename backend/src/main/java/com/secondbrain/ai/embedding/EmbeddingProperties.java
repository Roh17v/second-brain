package com.secondbrain.ai.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.secondbrain.ai.AiProviders;

/**
 * Provider-agnostic embedding settings.
 * Swap implementation with {@code app.embedding.provider} only.
 */
@ConfigurationProperties(prefix = "app.embedding")
public class EmbeddingProperties {

	/**
	 * Which {@link EmbeddingClient} bean is active.
	 * {@link AiProviders#EMBEDDING_OLLAMA}, {@link AiProviders#EMBEDDING_GEMINI},
	 * {@link AiProviders#EMBEDDING_HASHING}.
	 */
	private String provider = AiProviders.EMBEDDING_OLLAMA;

	/**
	 * Provider base URL (Ollama host, Gemini root, etc.). Empty = client default.
	 */
	private String baseUrl = "http://localhost:11434";

	/**
	 * Model id for the active provider (e.g. nomic-embed-text, gemini-embedding-2).
	 */
	private String model = "nomic-embed-text";

	/**
	 * Expected vector length. Must match the model (and pgvector column after migrate/re-embed).
	 */
	private int dimensions = 768;

	/**
	 * API key for cloud providers. Prefer env {@code GEMINI_API_KEY} / provider-specific vars.
	 * Never commit secrets.
	 */
	private String apiKey = "";

	/**
	 * Optional output dimensionality for Matryoshka models (Gemini). 0 = provider default.
	 */
	private int outputDimensionality = 0;

	private int timeoutSeconds = 60;

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public int getDimensions() {
		return dimensions;
	}

	public void setDimensions(int dimensions) {
		this.dimensions = dimensions;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public int getOutputDimensionality() {
		return outputDimensionality;
	}

	public void setOutputDimensionality(int outputDimensionality) {
		this.outputDimensionality = outputDimensionality;
	}

	public int getTimeoutSeconds() {
		return timeoutSeconds;
	}

	public void setTimeoutSeconds(int timeoutSeconds) {
		this.timeoutSeconds = timeoutSeconds;
	}

	public boolean hasApiKey() {
		return apiKey != null && !apiKey.isBlank();
	}
}
