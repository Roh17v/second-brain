package com.secondbrain.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * GIS credential: either an ID token (One Tap / button) or an OAuth access token (popup).
 * Exactly one should be set.
 */
public record GoogleAuthRequest(
		@Size(max = 8192)
		String idToken,

		@Size(max = 8192)
		String accessToken
) {

	@AssertTrue(message = "idToken or accessToken is required")
	public boolean isTokenPresent() {
		return (idToken != null && !idToken.isBlank())
				|| (accessToken != null && !accessToken.isBlank());
	}
}
