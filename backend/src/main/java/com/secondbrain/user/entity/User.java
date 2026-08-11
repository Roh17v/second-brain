package com.secondbrain.user.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true, length = 320)
	private String email;

	@Column(nullable = false, length = 200)
	private String name;

	/**
	 * BCrypt password hash. Null for Google-only accounts (no password set).
	 */
	@Column(name = "password_hash", length = 100)
	private String passwordHash;

	/**
	 * Google account subject ({@code sub}) when the user has signed in with Google.
	 * Unique when present so one Google account maps to one user.
	 */
	@Column(name = "google_id", unique = true, length = 255)
	private String googleId;

	/**
	 * False until email OTP verification succeeds (or Google verifies the email).
	 * Existing rows: set true once after migration if needed.
	 */
	@Column(name = "email_verified", nullable = false)
	private boolean emailVerified = false;

	/** BCrypt hash of the current 6-digit OTP (never store plaintext). */
	@Column(name = "email_otp_hash", length = 100)
	private String emailOtpHash;

	@Column(name = "email_otp_expires_at")
	private Instant emailOtpExpiresAt;

	@Column(name = "email_otp_attempts", nullable = false)
	private int emailOtpAttempts = 0;

	@Column(name = "email_otp_last_sent_at")
	private Instant emailOtpLastSentAt;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected User() {
		// JPA
	}

	public User(String email, String name, String passwordHash) {
		this.email = email;
		this.name = name;
		this.passwordHash = passwordHash;
		this.emailVerified = false;
	}

	/** Google (or other federated) signup — email already verified by the provider. */
	public static User googleUser(String email, String name, String googleId) {
		User user = new User(email, name, null);
		user.googleId = googleId;
		user.emailVerified = true;
		return user;
	}

	public boolean hasPassword() {
		return passwordHash != null && !passwordHash.isBlank();
	}

	public boolean isGoogleLinked() {
		return googleId != null && !googleId.isBlank();
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public void clearEmailOtp() {
		this.emailOtpHash = null;
		this.emailOtpExpiresAt = null;
		this.emailOtpAttempts = 0;
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public String getGoogleId() {
		return googleId;
	}

	public void setGoogleId(String googleId) {
		this.googleId = googleId;
	}

	public boolean isEmailVerified() {
		return emailVerified;
	}

	public void setEmailVerified(boolean emailVerified) {
		this.emailVerified = emailVerified;
	}

	public String getEmailOtpHash() {
		return emailOtpHash;
	}

	public void setEmailOtpHash(String emailOtpHash) {
		this.emailOtpHash = emailOtpHash;
	}

	public Instant getEmailOtpExpiresAt() {
		return emailOtpExpiresAt;
	}

	public void setEmailOtpExpiresAt(Instant emailOtpExpiresAt) {
		this.emailOtpExpiresAt = emailOtpExpiresAt;
	}

	public int getEmailOtpAttempts() {
		return emailOtpAttempts;
	}

	public void setEmailOtpAttempts(int emailOtpAttempts) {
		this.emailOtpAttempts = emailOtpAttempts;
	}

	public Instant getEmailOtpLastSentAt() {
		return emailOtpLastSentAt;
	}

	public void setEmailOtpLastSentAt(Instant emailOtpLastSentAt) {
		this.emailOtpLastSentAt = emailOtpLastSentAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
