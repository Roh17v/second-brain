package com.secondbrain.ai.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.embedding")
public class EmbeddingProperties {

	/**
	 * Provider id: ollama | hashing (tests / offline).
	 */
	private String provider = "ollama";

	private String baseUrl = "http://localhost:11434";

	private String model = "nomic-embed-text";

	/**
	 * Must match the embedding model output size (nomic-embed-text = 768).
	 */
	private int dimensions = 768;

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
}
