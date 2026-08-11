package com.secondbrain.auth.google;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.secondbrain.auth.dto.GoogleAuthRequest;
import com.secondbrain.common.exception.BadRequestException;
import com.secondbrain.common.exception.UnauthorizedException;

/**
 * Resolves a trusted {@link GoogleIdentity} from either:
 * <ul>
 *   <li>GIS ID token (audience = web client ID), or</li>
 *   <li>OAuth access token via Google userinfo (popup flow)</li>
 * </ul>
 */
@Service
public class GoogleIdTokenVerifierService {

	private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifierService.class);
	private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

	private final GoogleAuthProperties properties;
	private final RestClient restClient;
	private volatile GoogleIdTokenVerifier verifier;

	public GoogleIdTokenVerifierService(GoogleAuthProperties properties) {
		this.properties = properties;
		this.restClient = RestClient.builder()
				.baseUrl("https://www.googleapis.com")
				.build();
	}

	public GoogleIdentity resolve(GoogleAuthRequest request) {
		if (!properties.isEnabled()) {
			throw new BadRequestException("Google sign-in is not configured on this server.");
		}
		if (request.idToken() != null && !request.idToken().isBlank()) {
			return verifyIdToken(request.idToken());
		}
		if (request.accessToken() != null && !request.accessToken().isBlank()) {
			return verifyAccessToken(request.accessToken());
		}
		throw new BadRequestException("idToken or accessToken is required");
	}

	public GoogleIdentity verify(String idToken) {
		return verifyIdToken(idToken);
	}

	public GoogleIdentity verifyIdToken(String idToken) {
		if (!properties.isEnabled()) {
			throw new BadRequestException("Google sign-in is not configured on this server.");
		}
		if (idToken == null || idToken.isBlank()) {
			throw new BadRequestException("idToken is required");
		}

		GoogleIdToken token;
		try {
			token = verifier().verify(idToken.trim());
		}
		catch (GeneralSecurityException | IOException ex) {
			log.warn("Google ID token verification failed: {}", ex.getMessage());
			throw new UnauthorizedException("Invalid Google sign-in token");
		}

		if (token == null) {
			throw new UnauthorizedException("Invalid or expired Google sign-in token");
		}

		GoogleIdToken.Payload payload = token.getPayload();
		String email = payload.getEmail();
		if (email == null || email.isBlank()) {
			throw new UnauthorizedException("Google account did not provide an email address");
		}
		if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
			throw new UnauthorizedException("Google email is not verified");
		}

		String subject = payload.getSubject();
		if (subject == null || subject.isBlank()) {
			throw new UnauthorizedException("Invalid Google sign-in token");
		}

		String name = stringClaim(payload, "name");
		if (name == null || name.isBlank()) {
			name = email.split("@")[0];
		}

		return new GoogleIdentity(subject, email.trim().toLowerCase(), name.trim(), true);
	}

	/**
	 * Popup OAuth access token → userinfo. Confirms the token is valid and email is verified.
	 */
	public GoogleIdentity verifyAccessToken(String accessToken) {
		if (!properties.isEnabled()) {
			throw new BadRequestException("Google sign-in is not configured on this server.");
		}
		if (accessToken == null || accessToken.isBlank()) {
			throw new BadRequestException("accessToken is required");
		}

		Map<String, Object> body;
		try {
			body = restClient.get()
					.uri(USERINFO_URL)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken.trim())
					.accept(MediaType.APPLICATION_JSON)
					.retrieve()
					.body(new ParameterizedTypeReference<>() {
					});
		}
		catch (RestClientResponseException ex) {
			log.warn("Google userinfo failed: status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
			throw new UnauthorizedException("Invalid or expired Google sign-in token");
		}
		catch (RestClientException ex) {
			log.warn("Google userinfo request failed: {}", ex.getMessage());
			throw new UnauthorizedException("Could not verify Google sign-in");
		}

		if (body == null) {
			throw new UnauthorizedException("Invalid Google sign-in token");
		}

		String subject = asString(body.get("sub"));
		String email = asString(body.get("email"));
		if (email == null || email.isBlank()) {
			throw new UnauthorizedException("Google account did not provide an email address");
		}
		if (subject == null || subject.isBlank()) {
			throw new UnauthorizedException("Invalid Google sign-in token");
		}

		boolean emailVerified = Boolean.TRUE.equals(body.get("email_verified"))
				|| "true".equalsIgnoreCase(asString(body.get("email_verified")));
		if (!emailVerified) {
			throw new UnauthorizedException("Google email is not verified");
		}

		String name = asString(body.get("name"));
		if (name == null || name.isBlank()) {
			name = email.split("@")[0];
		}

		return new GoogleIdentity(subject, email.trim().toLowerCase(), name.trim(), true);
	}

	private GoogleIdTokenVerifier verifier() {
		GoogleIdTokenVerifier current = verifier;
		if (current != null) {
			return current;
		}
		synchronized (this) {
			if (verifier == null) {
				verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
						.setAudience(Collections.singletonList(properties.getClientId().trim()))
						.build();
			}
			return verifier;
		}
	}

	private static String stringClaim(GoogleIdToken.Payload payload, String key) {
		Object value = payload.get(key);
		return value instanceof String s ? s : null;
	}

	private static String asString(Object value) {
		return value instanceof String s ? s : value != null ? String.valueOf(value) : null;
	}
}
