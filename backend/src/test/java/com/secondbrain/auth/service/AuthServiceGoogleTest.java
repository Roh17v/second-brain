package com.secondbrain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.secondbrain.auth.dto.GoogleAuthRequest;
import com.secondbrain.auth.dto.LoginRequest;
import com.secondbrain.auth.google.GoogleIdTokenVerifierService;
import com.secondbrain.auth.google.GoogleIdentity;
import com.secondbrain.common.exception.ConflictException;
import com.secondbrain.common.exception.UnauthorizedException;
import com.secondbrain.security.JwtService;
import com.secondbrain.user.entity.User;
import com.secondbrain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceGoogleTest {

	@Mock
	UserRepository userRepository;
	@Mock
	JwtService jwtService;
	@Mock
	AuthenticationManager authenticationManager;
	@Mock
	EmailVerificationService emailVerificationService;
	@Mock
	GoogleIdTokenVerifierService googleIdTokenVerifierService;

	PasswordEncoder encoder = new BCryptPasswordEncoder();
	AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(
				userRepository,
				encoder,
				jwtService,
				authenticationManager,
				emailVerificationService,
				googleIdTokenVerifierService
		);
	}

	@Test
	void google_newUser_createsVerifiedGoogleOnlyAccount() {
		when(googleIdTokenVerifierService.resolve(any())).thenReturn(
				new GoogleIdentity("g-sub-1", "new@example.com", "New User", true)
		);
		when(userRepository.findByGoogleId("g-sub-1")).thenReturn(Optional.empty());
		when(userRepository.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());
		when(userRepository.save(any(User.class))).thenAnswer(inv -> {
			User u = inv.getArgument(0);
			ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
			return u;
		});
		when(jwtService.generateToken(any(), anyString())).thenReturn("jwt");

		var res = authService.loginWithGoogle(new GoogleAuthRequest("tok", null));

		assertThat(res.accessToken()).isEqualTo("jwt");
		assertThat(res.email()).isEqualTo("new@example.com");
		assertThat(res.name()).isEqualTo("New User");

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		User saved = captor.getValue();
		assertThat(saved.getGoogleId()).isEqualTo("g-sub-1");
		assertThat(saved.isEmailVerified()).isTrue();
		assertThat(saved.hasPassword()).isFalse();
	}

	@Test
	void google_existingPasswordAccount_linksGoogleAndVerifies() {
		User existing = new User("old@example.com", "Old Name", encoder.encode("password12"));
		existing.setEmailVerified(false);
		existing.setEmailOtpHash("hash");
		ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());

		when(googleIdTokenVerifierService.resolve(any())).thenReturn(
				new GoogleIdentity("g-sub-2", "old@example.com", "Google Name", true)
		);
		when(userRepository.findByGoogleId("g-sub-2")).thenReturn(Optional.empty());
		when(userRepository.findByEmailIgnoreCase("old@example.com")).thenReturn(Optional.of(existing));
		when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
		when(jwtService.generateToken(any(), anyString())).thenReturn("jwt");

		var res = authService.loginWithGoogle(new GoogleAuthRequest("tok", null));

		assertThat(res.email()).isEqualTo("old@example.com");
		assertThat(existing.getGoogleId()).isEqualTo("g-sub-2");
		assertThat(existing.isEmailVerified()).isTrue();
		assertThat(existing.hasPassword()).isTrue();
		assertThat(existing.getEmailOtpHash()).isNull();
		// Keep original name when already set
		assertThat(existing.getName()).isEqualTo("Old Name");
	}

	@Test
	void google_alreadyLinked_signsIn() {
		User existing = User.googleUser("g@example.com", "G User", "g-sub-3");
		ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());

		when(googleIdTokenVerifierService.resolve(any())).thenReturn(
				new GoogleIdentity("g-sub-3", "g@example.com", "G User", true)
		);
		when(userRepository.findByGoogleId("g-sub-3")).thenReturn(Optional.of(existing));
		when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
		when(jwtService.generateToken(any(), anyString())).thenReturn("jwt");

		var res = authService.loginWithGoogle(new GoogleAuthRequest(null, "access-tok"));

		assertThat(res.accessToken()).isEqualTo("jwt");
		verify(userRepository, never()).findByEmailIgnoreCase(anyString());
	}

	@Test
	void google_emailLinkedToDifferentSubject_conflicts() {
		User existing = User.googleUser("clash@example.com", "Clash", "other-sub");
		ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());

		when(googleIdTokenVerifierService.resolve(any())).thenReturn(
				new GoogleIdentity("new-sub", "clash@example.com", "Clash", true)
		);
		when(userRepository.findByGoogleId("new-sub")).thenReturn(Optional.empty());
		when(userRepository.findByEmailIgnoreCase("clash@example.com")).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> authService.loginWithGoogle(new GoogleAuthRequest("tok", null)))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("different Google account");
	}

	@Test
	void passwordLogin_googleOnly_clearMessage() {
		User googleOnly = User.googleUser("g@example.com", "G", "g-sub");
		when(userRepository.findByEmailIgnoreCase("g@example.com")).thenReturn(Optional.of(googleOnly));

		assertThatThrownBy(() -> authService.login(new LoginRequest("g@example.com", "whatever12")))
				.isInstanceOf(UnauthorizedException.class)
				.hasMessageContaining("Continue with Google")
				.hasMessageContaining("Forgot password");
		verify(authenticationManager, never()).authenticate(any());
	}
}
