package com.secondbrain.auth.dto;

import java.util.UUID;

public record AuthResponse(
		String accessToken,
		String tokenType,
		UUID userId,
		String email,
		String name
) {

	public static AuthResponse bearer(String token, UUID userId, String email, String name) {
		return new AuthResponse(token, "Bearer", userId, email, name);
	}
}
