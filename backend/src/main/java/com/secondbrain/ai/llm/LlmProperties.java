package com.secondbrain.ai.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.secondbrain.ai.AiProviders;

/**
 * Provider-agnostic chat LLM settings.
 * Swap implementation with {@code app.llm.provider} only.
 */
@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {

	/**
	 * Which {@link LlmClient} bean is active.
	 * {@link AiProviders#LLM_OLLAMA}, {@link AiProviders#LLM_GEMINI},
	 * {@link AiProviders#LLM_GROQ}, {@link AiProviders#LLM_ECHO}.
	 */
	private String provider = AiProviders.LLM_OLLAMA;

	/**
	 * Provider base URL. Empty may mean client default (e.g. Gemini public API).
	 */
	private String baseUrl = "http://localhost:11434";

	/**
	 * Model id (e.g. qwen3:4b, gemini-2.5-flash).
	 */
	private String model = "qwen3:4b";

	private double temperature = 0.2;

	/**
	 * Groq reasoning models (e.g. Qwen 3.6): {@code none} | {@code default} | {@code low} | …
	 * Default {@code none} so chain-of-thought is not streamed to the UI.
	 * Empty string = omit the parameter (provider default).
	 */
	private String reasoningEffort = "none";

	/**
	 * API key for cloud providers. Prefer env; never commit.
	 */
	private String apiKey = "";

	private int timeoutSeconds = 180;

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

	public String getReasoningEffort() {
		return reasoningEffort;
	}

	public void setReasoningEffort(String reasoningEffort) {
		this.reasoningEffort = reasoningEffort;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
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
