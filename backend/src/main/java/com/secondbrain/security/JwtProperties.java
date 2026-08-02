package com.secondbrain.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

	/**
	 * HMAC secret. Must be long enough for HS256 (recommend 32+ characters).
	 */
	private String secret;

	/**
	 * Token lifetime in milliseconds. Default: 24 hours.
	 */
	private long expirationMs = 86_400_000L;

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}

	public long getExpirationMs() {
		return expirationMs;
	}

	public void setExpirationMs(long expirationMs) {
		this.expirationMs = expirationMs;
	}
}
