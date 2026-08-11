package com.secondbrain.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev/test email sender — logs content (including OTP) instead of sending.
 */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "logging", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

	@Override
	public void send(String toEmail, String subject, String htmlBody, String textBody) {
		log.info(
				"""
						[email:logging] to={} subject={}
						---
						{}
						---
						""",
				toEmail,
				subject,
				textBody != null && !textBody.isBlank() ? textBody : htmlBody
		);
	}
}
