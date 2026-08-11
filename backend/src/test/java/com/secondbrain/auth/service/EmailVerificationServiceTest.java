package com.secondbrain.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.secondbrain.common.exception.BadRequestException;
import com.secondbrain.email.EmailProperties;
import com.secondbrain.email.EmailSender;
import com.secondbrain.user.entity.User;
import com.secondbrain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

	@Mock
	UserRepository userRepository;
	@Mock
	EmailSender emailSender;

	PasswordEncoder encoder = new BCryptPasswordEncoder();
	EmailProperties props = new EmailProperties();
	EmailVerificationService service;

	@BeforeEach
	void setUp() {
		props.setOtpTtlMinutes(10);
		props.setResendCooldownSeconds(60);
		props.setMaxOtpAttempts(5);
		service = new EmailVerificationService(userRepository, encoder, emailSender, props);
		lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void issueAndSendEmailsCode() {
		User user = new User("a@test.com", "Ada", encoder.encode("password12"));
		service.issueAndSendOtp(user);

		assertTrue(user.getEmailOtpHash() != null);
		assertTrue(user.getEmailOtpExpiresAt().isAfter(Instant.now()));
		verify(emailSender).send(eq("a@test.com"), anyString(), anyString(), anyString());
	}

	@Test
	void verifyAcceptsCorrectCode() {
		User user = new User("a@test.com", "Ada", encoder.encode("password12"));
		AtomicReference<String> sentCode = new AtomicReference<>();

		// Capture plaintext from email text body
		org.mockito.Mockito.doAnswer(inv -> {
			String text = inv.getArgument(3);
			// "code is: 123456"
			var m = java.util.regex.Pattern.compile("(\\d{6})").matcher(text);
			assertTrue(m.find());
			sentCode.set(m.group(1));
			return null;
		}).when(emailSender).send(anyString(), anyString(), anyString(), anyString());

		service.issueAndSendOtp(user);
		User verified = service.verifyOtp(user, sentCode.get());

		assertTrue(verified.isEmailVerified());
		assertEquals(null, verified.getEmailOtpHash());
	}

	@Test
	void verifyRejectsWrongCode() {
		User user = new User("a@test.com", "Ada", encoder.encode("password12"));
		user.setEmailOtpHash(encoder.encode("123456"));
		user.setEmailOtpExpiresAt(Instant.now().plusSeconds(600));
		user.setEmailOtpAttempts(0);

		assertThrows(BadRequestException.class, () -> service.verifyOtp(user, "000000"));
		assertEquals(1, user.getEmailOtpAttempts());
		verify(emailSender, never()).send(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void resendRespectsCooldown() {
		User user = new User("a@test.com", "Ada", encoder.encode("password12"));
		user.setEmailOtpLastSentAt(Instant.now());
		assertThrows(BadRequestException.class, () -> service.issueAndSendOtp(user));
	}
}
