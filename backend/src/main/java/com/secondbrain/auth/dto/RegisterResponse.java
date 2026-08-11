package com.secondbrain.auth.dto;

/**
 * Register either completes auth immediately (legacy/disabled verification)
 * or requires email OTP verification.
 */
public record RegisterResponse(
		/**
		 * {@code VERIFICATION_REQUIRED} or {@code AUTHENTICATED}
		 */
		String status,
		String email,
		String message,
		/** Non-null only when status is AUTHENTICATED */
		AuthResponse auth
) {

	public static RegisterResponse verificationRequired(String email) {
		return new RegisterResponse(
				"VERIFICATION_REQUIRED",
				email,
				"We sent a 6-digit verification code to your email.",
				null
		);
	}

	public static RegisterResponse authenticated(AuthResponse auth) {
		return new RegisterResponse(
				"AUTHENTICATED",
				auth.email(),
				"Account created.",
				auth
		);
	}
}
