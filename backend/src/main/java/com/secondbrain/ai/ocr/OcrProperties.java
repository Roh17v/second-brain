package com.secondbrain.ai.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ocr")
public class OcrProperties {

	/**
	 * Provider: {@code none} (disabled) or {@code mistral}.
	 */
	private String provider = "none";

	/**
	 * Mistral API key. Prefer env {@code MISTRAL_API_KEY}.
	 */
	private String apiKey = "";

	private String baseUrl = "https://api.mistral.ai";

	/**
	 * OCR model id, e.g. mistral-ocr-latest or mistral-ocr-4-0.
	 */
	private String model = "mistral-ocr-latest";

	/**
	 * If PDFBox extracts fewer than this many non-whitespace characters, fall back to OCR.
	 */
	private int minTextChars = 40;

	/**
	 * HTTP timeout for OCR requests (large multi-page PDFs can be slow).
	 */
	private int timeoutSeconds = 180;

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
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

	public int getMinTextChars() {
		return minTextChars;
	}

	public void setMinTextChars(int minTextChars) {
		this.minTextChars = minTextChars;
	}

	public int getTimeoutSeconds() {
		return timeoutSeconds;
	}

	public void setTimeoutSeconds(int timeoutSeconds) {
		this.timeoutSeconds = timeoutSeconds;
	}

	public boolean isEnabled() {
		return provider != null && !"none".equalsIgnoreCase(provider.trim());
	}

	public boolean hasApiKey() {
		return apiKey != null && !apiKey.isBlank();
	}
}
