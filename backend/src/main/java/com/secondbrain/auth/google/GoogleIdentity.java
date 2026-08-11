package com.secondbrain.auth.google;

/**
 * Claims we trust after verifying a Google ID token.
 */
public record GoogleIdentity(
		String subject,
		String email,
		String name,
		boolean emailVerified
) {
}
