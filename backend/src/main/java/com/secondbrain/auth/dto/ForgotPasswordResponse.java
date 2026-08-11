package com.secondbrain.auth.dto;

/**
 * @param message user-facing text
 * @param intent  {@code set_password} (first time / Google), {@code reset_password}, or {@code unknown}
 *                (no account / generic — do not treat as enumeration in the UI)
 */
public record ForgotPasswordResponse(
		String message,
		String intent
) {
	public static final String INTENT_SET = "set_password";
	public static final String INTENT_RESET = "reset_password";
	public static final String INTENT_UNKNOWN = "unknown";

	public static ForgotPasswordResponse unknown(String message) {
		return new ForgotPasswordResponse(message, INTENT_UNKNOWN);
	}

	public static ForgotPasswordResponse setPassword(String message) {
		return new ForgotPasswordResponse(message, INTENT_SET);
	}

	public static ForgotPasswordResponse resetPassword(String message) {
		return new ForgotPasswordResponse(message, INTENT_RESET);
	}
}
