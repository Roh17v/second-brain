package com.secondbrain.ai.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {

	/**
	 * Provider: ollama | echo (tests/offline).
	 */
	private String provider = "ollama";

	private String baseUrl = "http://localhost:11434";

	/**
	 * Ollama model tag, e.g. qwen3:4b or qwen2.5:3b depending on what you pulled.
	 */
	private String model = "qwen3:4b";

	private double temperature = 0.2;

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

	public double getTemperature() {
		return temperature;
	}

	public void setTemperature(double temperature) {
		this.temperature = temperature;
	}
}
