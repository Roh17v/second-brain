package com.secondbrain.auth.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secondbrain.auth.dto.AuthResponse;
import com.secondbrain.auth.dto.ForgotPasswordRequest;
import com.secondbrain.auth.dto.ForgotPasswordResponse;
import com.secondbrain.auth.dto.GoogleAuthRequest;
import com.secondbrain.auth.dto.LoginRequest;
import com.secondbrain.auth.dto.MessageResponse;
import com.secondbrain.auth.dto.RegisterRequest;
import com.secondbrain.auth.dto.RegisterResponse;
import com.secondbrain.auth.dto.ResendVerificationRequest;
import com.secondbrain.auth.dto.ResetPasswordRequest;
import com.secondbrain.auth.dto.VerifyEmailRequest;
import com.secondbrain.auth.google.GoogleIdTokenVerifierService;
import com.secondbrain.auth.google.GoogleIdentity;
import com.secondbrain.common.exception.BadRequestException;
import com.secondbrain.common.exception.ConflictException;
import com.secondbrain.common.exception.UnauthorizedException;
import com.secondbrain.security.JwtService;
import com.secondbrain.security.UserPrincipal;
import com.secondbrain.user.entity.User;
import com.secondbrain.user.repository.UserRepository;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final AuthenticationManager authenticationManager;
	private final EmailVerificationService emailVerificationService;
	private final GoogleIdTokenVerifierService googleIdTokenVerifierService;

	public AuthService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			AuthenticationManager authenticationManager,
			EmailVerificationService emailVerificationService,
			GoogleIdTokenVerifierService googleIdTokenVerifierService
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.authenticationManager = authenticationManager;
		this.emailVerificationService = emailVerificationService;
		this.googleIdTokenVerifierService = googleIdTokenVerifierService;
	}

	@Transactional
	public RegisterResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.email());
		String name = request.name().trim();

		if (name.isEmpty()) {
			throw new BadRequestException("name must not be blank");
		}

		var existing = userRepository.findByEmailIgnoreCase(email);
		if (existing.isPresent()) {
			User user = existing.get();
			if (user.isEmailVerified()) {
				throw new ConflictException(accountExistsMessage(user));
			}
			if (user.isGoogleLinked()) {
				// Unverified + Google is unexpected; treat as existing Google account
				throw new ConflictException(accountExistsMessage(user));
			}
			// Unverified email/password: refresh credentials and resend OTP
			user.setName(name);
			user.setPasswordHash(passwordEncoder.encode(request.password()));
			userRepository.save(user);
			emailVerificationService.issueAndSendOtp(user);
			return RegisterResponse.verificationRequired(email);
		}

		String passwordHash = passwordEncoder.encode(request.password());
		User user = new User(email, name, passwordHash);
		user.setEmailVerified(false);
		User saved = userRepository.save(user);

		emailVerificationService.issueAndSendOtp(saved);
		return RegisterResponse.verificationRequired(email);
	}

	@Transactional
	public AuthResponse verifyEmail(VerifyEmailRequest request) {
		String email = normalizeEmail(request.email());
		User user = userRepository.findByEmailIgnoreCase(email)
				.orElseThrow(() -> new BadRequestException("Invalid email or code."));

		User verified = emailVerificationService.verifyOtp(user, request.code());
		return issueAuth(verified);
	}

	@Transactional
	public MessageResponse resendVerification(ResendVerificationRequest request) {
		String email = normalizeEmail(request.email());
		User user = userRepository.findByEmailIgnoreCase(email).orElse(null);

		// Generic message — do not reveal whether email exists
		String okMessage = "If an unverified account exists for this email, a new code has been sent.";

		if (user == null) {
			return new MessageResponse(okMessage);
		}
		if (user.isEmailVerified()) {
			return new MessageResponse("This email is already verified. You can sign in.");
		}
		if (!user.hasPassword()) {
			return new MessageResponse("This account uses Google Sign-In. Please continue with Google.");
		}

		emailVerificationService.issueAndSendOtp(user);
		return new MessageResponse(okMessage);
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		String email = normalizeEmail(request.email());

		var existing = userRepository.findByEmailIgnoreCase(email);
		if (existing.isEmpty()) {
			throw new UnauthorizedException(
					"No account exists for this email. Create an account or continue with Google."
			);
		}

		User user = existing.get();
		if (!user.hasPassword() && user.isGoogleLinked()) {
			throw new UnauthorizedException(
					"This account uses Google Sign-In. Continue with Google, or use Forgot password to set one."
			);
		}

		try {
			var authentication = authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(email, request.password())
			);

			UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
			if (!principal.isEnabled()) {
				throw new UnauthorizedException(
						"Please verify your email before signing in. Check your inbox for a code."
				);
			}

			return AuthResponse.bearer(
					jwtService.generateToken(principal.getId(), principal.getUsername()),
					principal.getId(),
					principal.getUsername(),
					principal.getName()
			);
		}
		catch (UnauthorizedException ex) {
			throw ex;
		}
		catch (DisabledException ex) {
			throw new UnauthorizedException(
					"Please verify your email before signing in. Check your inbox for a code."
			);
		}
		catch (org.springframework.security.core.AuthenticationException ex) {
			throw new UnauthorizedException("Invalid credentials.");
		}
	}

	/**
	 * Request a code to set or reset password (also for Google-only accounts).
	 * Email copy is tailored: first-time set (no password yet) vs reset (already has password).
	 * Unknown emails get a generic response (no enumeration).
	 */
	@Transactional
	public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
		String email = normalizeEmail(request.email());

		User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
		if (user == null) {
			return ForgotPasswordResponse.unknown(
					"If an account exists for this email, a password code has been sent."
			);
		}

		boolean firstTimeSet = emailVerificationService.issueAndSendPasswordResetOtp(user);
		if (firstTimeSet) {
			return ForgotPasswordResponse.setPassword(
					"We sent a code to set a password for this account. Check your email."
			);
		}
		return ForgotPasswordResponse.resetPassword(
				"We sent a code to reset your password. Check your email."
		);
	}

	/**
	 * Set or reset password after OTP. Marks email verified and returns a session JWT.
	 * Works for password users and Google-only users (adds a password alongside Google).
	 */
	@Transactional
	public AuthResponse resetPassword(ResetPasswordRequest request) {
		String email = normalizeEmail(request.email());
		User user = userRepository.findByEmailIgnoreCase(email)
				.orElseThrow(() -> new BadRequestException("Invalid email or code."));

		User unlocked = emailVerificationService.consumeOtpForPasswordChange(user, request.code());
		unlocked.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		User saved = userRepository.save(unlocked);
		return issueAuth(saved);
	}

	/**
	 * Sign in or sign up with Google (ID token or OAuth access token from the popup flow).
	 * <p>
	 * Account linking rules (Google has verified email ownership):
	 * <ul>
	 *   <li>Existing user with same {@code googleId} → sign in</li>
	 *   <li>Existing user with same email, no Google yet → link Google, mark verified, keep password</li>
	 *   <li>Existing user with same email but a different Google subject → conflict</li>
	 *   <li>No user → create verified Google-only account (no password)</li>
	 * </ul>
	 */
	@Transactional
	public AuthResponse loginWithGoogle(GoogleAuthRequest request) {
		GoogleIdentity identity = googleIdTokenVerifierService.resolve(request);
		String email = normalizeEmail(identity.email());
		String googleId = identity.subject();

		// 1) Already linked by Google subject
		var byGoogle = userRepository.findByGoogleId(googleId);
		if (byGoogle.isPresent()) {
			User user = byGoogle.get();
			// Keep profile name in sync if Google returns a better one and ours looks like a stub
			if (identity.name() != null && !identity.name().isBlank()
					&& (user.getName() == null || user.getName().isBlank())) {
				user.setName(identity.name());
			}
			if (!user.isEmailVerified()) {
				user.setEmailVerified(true);
				user.clearEmailOtp();
			}
			return issueAuth(userRepository.save(user));
		}

		// 2) Same email already registered (password and/or pending OTP)
		var byEmail = userRepository.findByEmailIgnoreCase(email);
		if (byEmail.isPresent()) {
			User user = byEmail.get();

			if (user.isGoogleLinked() && !googleId.equals(user.getGoogleId())) {
				throw new ConflictException(
						"This email is already linked to a different Google account. "
								+ "Sign in with that Google account or use email and password."
				);
			}

			// Link this Google account to the existing user (clean merge)
			user.setGoogleId(googleId);
			user.setEmailVerified(true);
			user.clearEmailOtp();
			if (identity.name() != null && !identity.name().isBlank()
					&& (user.getName() == null || user.getName().isBlank())) {
				user.setName(identity.name());
			}
			return issueAuth(userRepository.save(user));
		}

		// 3) Brand-new user
		String name = identity.name() != null && !identity.name().isBlank()
				? identity.name().trim()
				: email.split("@")[0];
		User created = User.googleUser(email, name, googleId);
		return issueAuth(userRepository.save(created));
	}

	private AuthResponse issueAuth(User user) {
		String token = jwtService.generateToken(user.getId(), user.getEmail());
		return AuthResponse.bearer(token, user.getId(), user.getEmail(), user.getName());
	}

	private static String accountExistsMessage(User user) {
		if (user.isGoogleLinked() && !user.hasPassword()) {
			return "An account with this email already exists. Please continue with Google.";
		}
		if (user.isGoogleLinked()) {
			return "An account with this email already exists. Sign in with your password or continue with Google.";
		}
		return "An account with this email already exists. Please sign in.";
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase();
	}
}
