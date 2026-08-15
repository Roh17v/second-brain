package com.secondbrain.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email")
public class EmailProperties {

	/**
	 * resend | logging (tests / local without key)
	 */
	private String provider = "logging";

	private String apiKey = "";

	/** e.g. SecondBrain &lt;onboarding@yourdomain.com&gt; — must be verified in Resend. */
	private String from = "SecondBrain <onboarding@resend.dev>";

	private String resendBaseUrl = "https://api.resend.com";

	/** Public SPA origin for links in transactional mail. */
	private String publicBaseUrl = "http://localhost:5173";

	/** OTP validity window. */
	private int otpTtlMinutes = 10;

	/** Minimum seconds between resend requests. */
	private int resendCooldownSeconds = 60;

	/** Max wrong OTP attempts before code is invalidated. */
	private int maxOtpAttempts = 5;

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

	public String getFrom() {
		return from;
	}

	public void setFrom(String from) {
		this.from = from;
	}

	public String getResendBaseUrl() {
		return resendBaseUrl;
	}

	public void setResendBaseUrl(String resendBaseUrl) {
		this.resendBaseUrl = resendBaseUrl;
	}

	public int getOtpTtlMinutes() {
		return otpTtlMinutes;
	}

	public void setOtpTtlMinutes(int otpTtlMinutes) {
		this.otpTtlMinutes = otpTtlMinutes;
	}

	public int getResendCooldownSeconds() {
		return resendCooldownSeconds;
	}

	public void setResendCooldownSeconds(int resendCooldownSeconds) {
		this.resendCooldownSeconds = resendCooldownSeconds;
	}

	public int getMaxOtpAttempts() {
		return maxOtpAttempts;
	}

	public void setMaxOtpAttempts(int maxOtpAttempts) {
		this.maxOtpAttempts = maxOtpAttempts;
	}

	public boolean hasApiKey() {
		return apiKey != null && !apiKey.isBlank();
	}

	public String getPublicBaseUrl() {
		return publicBaseUrl;
	}

	public void setPublicBaseUrl(String publicBaseUrl) {
		this.publicBaseUrl = publicBaseUrl;
	}
}
