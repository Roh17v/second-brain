package com.secondbrain.email;

/**
 * Port for transactional email. Implementations: Resend, logging (dev/test).
 */
public interface EmailSender {

	void send(String toEmail, String subject, String htmlBody, String textBody);
}
