package com.secondbrain.email;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.secondbrain.common.exception.BadRequestException;

/**
 * Sends mail via <a href="https://resend.com/docs/api-reference/emails/send-email">Resend API</a>.
 */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "resend")
public class ResendEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

	private final EmailProperties properties;
	private final RestClient restClient;

	public ResendEmailSender(EmailProperties properties) {
		this.properties = properties;
		this.restClient = RestClient.builder()
				.baseUrl(trimSlash(properties.getResendBaseUrl()))
				.build();
	}

	@Override
	public void send(String toEmail, String subject, String htmlBody, String textBody) {
		if (!properties.hasApiKey()) {
			throw new BadRequestException(
					"Email is not configured. Set RESEND_API_KEY and EMAIL_FROM."
			);
		}

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("from", properties.getFrom());
		body.put("to", List.of(toEmail));
		body.put("subject", subject);
		if (htmlBody != null && !htmlBody.isBlank()) {
			body.put("html", htmlBody);
		}
		if (textBody != null && !textBody.isBlank()) {
			body.put("text", textBody);
		}

		try {
			restClient.post()
					.uri("/emails")
					.contentType(MediaType.APPLICATION_JSON)
					.header("Authorization", "Bearer " + properties.getApiKey().trim())
					.body(body)
					.retrieve()
					.toBodilessEntity();
			log.info("Resend: verification email accepted for {}", maskEmail(toEmail));
		}
		catch (RestClientResponseException ex) {
			log.error(
					"Resend failed HTTP {} for {}: {}",
					ex.getStatusCode().value(),
					maskEmail(toEmail),
					truncate(ex.getResponseBodyAsString())
			);
			throw new BadRequestException(
					"Failed to send verification email. Please try again shortly."
			);
		}
		catch (Exception ex) {
			log.error("Resend request error for {}: {}", maskEmail(toEmail), ex.getMessage());
			throw new BadRequestException(
					"Failed to send verification email. Please try again shortly."
			);
		}
	}

	private static String trimSlash(String url) {
		if (url == null || url.isBlank()) {
			return "https://api.resend.com";
		}
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	private static String maskEmail(String email) {
		if (email == null || !email.contains("@")) {
			return "***";
		}
		int at = email.indexOf('@');
		String local = email.substring(0, at);
		String domain = email.substring(at);
		if (local.length() <= 2) {
			return "**" + domain;
		}
		return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
	}

	private static String truncate(String s) {
		if (s == null || s.isBlank()) {
			return "(empty)";
		}
		String t = s.replaceAll("\\s+", " ").trim();
		return t.length() > 300 ? t.substring(0, 300) + "…" : t;
	}
}
