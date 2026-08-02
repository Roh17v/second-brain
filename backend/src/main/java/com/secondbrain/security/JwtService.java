package com.secondbrain.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

	private final JwtProperties jwtProperties;
	private final SecretKey secretKey;

	public JwtService(JwtProperties jwtProperties) {
		this.jwtProperties = jwtProperties;
		this.secretKey = Keys.hmacShaKeyFor(
				jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)
		);
	}

	public String generateToken(UUID userId, String email) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + jwtProperties.getExpirationMs());

		return Jwts.builder()
				.subject(userId.toString())
				.claim("email", email)
				.issuedAt(now)
				.expiration(expiry)
				.signWith(secretKey)
				.compact();
	}

	public boolean isValid(String token) {
		try {
			parseClaims(token);
			return true;
		}
		catch (JwtException | IllegalArgumentException ex) {
			return false;
		}
	}

	public UUID extractUserId(String token) {
		return UUID.fromString(parseClaims(token).getSubject());
	}

	public String extractEmail(String token) {
		return parseClaims(token).get("email", String.class);
	}

	private Claims parseClaims(String token) {
		return Jwts.parser()
				.verifyWith(secretKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}
}
