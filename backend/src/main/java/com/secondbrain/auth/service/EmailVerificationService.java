package com.secondbrain.auth.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secondbrain.common.exception.BadRequestException;
import com.secondbrain.email.EmailProperties;
import com.secondbrain.email.EmailSender;
import com.secondbrain.user.entity.User;
import com.secondbrain.user.repository.UserRepository;

/**
 * Issues and validates 6-digit email OTPs. Codes are stored hashed only.
 */
@Service
public class EmailVerificationService {

	private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
	private static final SecureRandom RANDOM = new SecureRandom();

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final EmailSender emailSender;
	private final EmailProperties emailProperties;

	public EmailVerificationService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			EmailSender emailSender,
			EmailProperties emailProperties
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.emailSender = emailSender;
		this.emailProperties = emailProperties;
	}

	@Transactional
	public void issueAndSendOtp(User user) {
		if (user.isEmailVerified()) {
			throw new BadRequestException("Email is already verified");
		}
		issueCode(user, OtpPurpose.SIGNUP_VERIFY);
	}

	/**
	 * Password reset or first-time set-password for existing accounts (including Google-only).
	 * Email copy depends on whether the user already has a password.
	 *
	 * @return true if this is a first-time set (no password yet), false if reset
	 */
	@Transactional
	public boolean issueAndSendPasswordResetOtp(User user) {
		boolean firstTimeSet = !user.hasPassword();
		issueCode(user, firstTimeSet ? OtpPurpose.SET_PASSWORD : OtpPurpose.RESET_PASSWORD);
		return firstTimeSet;
	}

	/**
	 * @return user now marked email-verified with OTP cleared
	 */
	@Transactional
	public User verifyOtp(User user, String rawCode) {
		if (user.isEmailVerified()) {
			return user;
		}
		assertValidOtp(user, rawCode);
		user.setEmailVerified(true);
		user.clearEmailOtp();
		return userRepository.save(user);
	}

	/**
	 * Validates OTP and clears it (does not change password). Marks email verified.
	 * Used for forgot / set password after the code is confirmed.
	 */
	@Transactional
	public User consumeOtpForPasswordChange(User user, String rawCode) {
		assertValidOtp(user, rawCode);
		user.setEmailVerified(true);
		user.clearEmailOtp();
		return userRepository.save(user);
	}

	private void issueCode(User user, OtpPurpose purpose) {
		Instant now = Instant.now();
		if (user.getEmailOtpLastSentAt() != null) {
			long secondsSince = Duration.between(user.getEmailOtpLastSentAt(), now).getSeconds();
			int cooldown = emailProperties.getResendCooldownSeconds();
			if (secondsSince < cooldown) {
				long wait = cooldown - secondsSince;
				throw new BadRequestException(
						"Please wait " + wait + " seconds before requesting another code."
				);
			}
		}

		String code = generateSixDigitCode();
		user.setEmailOtpHash(passwordEncoder.encode(code));
		user.setEmailOtpExpiresAt(now.plus(Duration.ofMinutes(emailProperties.getOtpTtlMinutes())));
		user.setEmailOtpAttempts(0);
		user.setEmailOtpLastSentAt(now);
		userRepository.save(user);

		if (purpose == OtpPurpose.SET_PASSWORD || purpose == OtpPurpose.RESET_PASSWORD) {
			boolean firstTimeSet = purpose == OtpPurpose.SET_PASSWORD;
			sendPasswordChangeEmail(user.getEmail(), user.getName(), code, firstTimeSet);
			log.info(
					"Password-{} OTP issued for userId={} email={}",
					firstTimeSet ? "set" : "reset",
					user.getId(),
					mask(user.getEmail())
			);
		}
		else {
			sendVerificationEmail(user.getEmail(), user.getName(), code);
			log.info("Verification OTP issued for userId={} email={}", user.getId(), mask(user.getEmail()));
		}
	}

	private void assertValidOtp(User user, String rawCode) {
		String code = rawCode == null ? "" : rawCode.trim().replaceAll("\\s+", "");
		if (!code.matches("\\d{6}")) {
			throw new BadRequestException("Enter the 6-digit code from your email.");
		}

		if (user.getEmailOtpHash() == null || user.getEmailOtpExpiresAt() == null) {
			throw new BadRequestException("No active verification code. Please request a new one.");
		}

		if (Instant.now().isAfter(user.getEmailOtpExpiresAt())) {
			user.clearEmailOtp();
			userRepository.save(user);
			throw new BadRequestException("This code has expired. Please request a new one.");
		}

		if (user.getEmailOtpAttempts() >= emailProperties.getMaxOtpAttempts()) {
			user.clearEmailOtp();
			userRepository.save(user);
			throw new BadRequestException("Too many attempts. Please request a new code.");
		}

		if (!passwordEncoder.matches(code, user.getEmailOtpHash())) {
			user.setEmailOtpAttempts(user.getEmailOtpAttempts() + 1);
			userRepository.save(user);
			int left = emailProperties.getMaxOtpAttempts() - user.getEmailOtpAttempts();
			throw new BadRequestException(
					left > 0
							? "Invalid code. " + left + " attempt(s) remaining."
							: "Too many attempts. Please request a new code."
			);
		}
	}

	private enum OtpPurpose {
		SIGNUP_VERIFY,
		/** Google-only / no password yet */
		SET_PASSWORD,
		/** User already has a password */
		RESET_PASSWORD
	}

	private void sendVerificationEmail(String email, String name, String code) {
		String safeName = name == null || name.isBlank() ? "there" : name.trim();
		int ttl = emailProperties.getOtpTtlMinutes();
		String subject = "Your SecondBrain verification code";
		String text = """
				Hi %s,

				Your SecondBrain verification code is: %s

				This code expires in %d minutes. If you didn't create an account, you can ignore this email.

				— SecondBrain
				""".formatted(safeName, code, ttl);

		String html = """
				<div style="font-family:system-ui,sans-serif;max-width:480px;margin:0 auto;padding:24px;color:#0f172a">
				  <h2 style="margin:0 0 12px">Verify your email</h2>
				  <p style="margin:0 0 16px">Hi %s,</p>
				  <p style="margin:0 0 16px">Use this code to finish creating your SecondBrain account:</p>
				  <p style="font-size:28px;font-weight:700;letter-spacing:6px;margin:24px 0;color:#1d4ed8">%s</p>
				  <p style="margin:0 0 8px;color:#64748b;font-size:14px">Expires in %d minutes.</p>
				  <p style="margin:16px 0 0;color:#64748b;font-size:13px">If you didn't sign up, you can ignore this email.</p>
				</div>
				""".formatted(escapeHtml(safeName), code, ttl);

		emailSender.send(email, subject, html, text);
	}

	/**
	 * @param firstTimeSet true → set password (e.g. Google signup); false → reset existing password
	 */
	private void sendPasswordChangeEmail(String email, String name, String code, boolean firstTimeSet) {
		String safeName = name == null || name.isBlank() ? "there" : name.trim();
		int ttl = emailProperties.getOtpTtlMinutes();

		if (firstTimeSet) {
			String subject = "Set a password for your SecondBrain account";
			String text = """
					Hi %s,

					You asked to set a password for your SecondBrain account (for example if you signed up with Google).

					Your code is: %s

					This code expires in %d minutes. If you didn't request this, you can ignore this email — your account stays protected with Google Sign-In.

					— SecondBrain
					""".formatted(safeName, code, ttl);

			String html = """
					<div style="font-family:system-ui,sans-serif;max-width:480px;margin:0 auto;padding:24px;color:#0f172a">
					  <h2 style="margin:0 0 12px">Set a password</h2>
					  <p style="margin:0 0 16px">Hi %s,</p>
					  <p style="margin:0 0 16px">Use this code to <strong>set a password</strong> on your SecondBrain account.
					  After that you can sign in with email and password, or keep using Google.</p>
					  <p style="font-size:28px;font-weight:700;letter-spacing:6px;margin:24px 0;color:#1d4ed8">%s</p>
					  <p style="margin:0 0 8px;color:#64748b;font-size:14px">Expires in %d minutes.</p>
					  <p style="margin:16px 0 0;color:#64748b;font-size:13px">If you didn't request this, ignore this email. Google Sign-In still works.</p>
					</div>
					""".formatted(escapeHtml(safeName), code, ttl);

			emailSender.send(email, subject, html, text);
			return;
		}

		String subject = "Reset your SecondBrain password";
		String text = """
				Hi %s,

				We received a request to reset the password for your SecondBrain account.

				Your code is: %s

				This code expires in %d minutes. If you didn't request a password reset, you can ignore this email — your current password stays the same.

				— SecondBrain
				""".formatted(safeName, code, ttl);

		String html = """
				<div style="font-family:system-ui,sans-serif;max-width:480px;margin:0 auto;padding:24px;color:#0f172a">
				  <h2 style="margin:0 0 12px">Reset your password</h2>
				  <p style="margin:0 0 16px">Hi %s,</p>
				  <p style="margin:0 0 16px">Use this code to <strong>reset</strong> your SecondBrain password:</p>
				  <p style="font-size:28px;font-weight:700;letter-spacing:6px;margin:24px 0;color:#1d4ed8">%s</p>
				  <p style="margin:0 0 8px;color:#64748b;font-size:14px">Expires in %d minutes.</p>
				  <p style="margin:16px 0 0;color:#64748b;font-size:13px">If you didn't request this, ignore this email. Your current password is unchanged.</p>
				</div>
				""".formatted(escapeHtml(safeName), code, ttl);

		emailSender.send(email, subject, html, text);
	}

	private static String generateSixDigitCode() {
		int n = RANDOM.nextInt(1_000_000);
		return String.format("%06d", n);
	}

	private static String mask(String email) {
		if (email == null || !email.contains("@")) {
			return "***";
		}
		int at = email.indexOf('@');
		return email.charAt(0) + "***" + email.substring(at);
	}

	private static String escapeHtml(String s) {
		return s.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;");
	}
}
