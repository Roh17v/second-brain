package com.secondbrain.auth.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.google")
public class GoogleAuthProperties {

	/**
	 * OAuth 2.0 Web client ID from Google Cloud Console (same value as the SPA).
	 * Empty = Google sign-in disabled.
	 */
	private String clientId = "";

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public boolean isEnabled() {
		return clientId != null && !clientId.isBlank();
	}
}
