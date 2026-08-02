package com.secondbrain.auth.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secondbrain.auth.dto.AuthResponse;
import com.secondbrain.auth.dto.LoginRequest;
import com.secondbrain.auth.dto.RegisterRequest;
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

	public AuthService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			AuthenticationManager authenticationManager
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.authenticationManager = authenticationManager;
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.email());
		String name = request.name().trim();

		if (name.isEmpty()) {
			throw new IllegalArgumentException("name must not be blank");
		}

		if (userRepository.existsByEmailIgnoreCase(email)) {
			throw new ConflictException("User with email already exists: " + email);
		}

		String passwordHash = passwordEncoder.encode(request.password());
		User user = new User(email, name, passwordHash);
		User saved = userRepository.save(user);

		String token = jwtService.generateToken(saved.getId(), saved.getEmail());
		return AuthResponse.bearer(token, saved.getId(), saved.getEmail(), saved.getName());
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		String email = normalizeEmail(request.email());

		try {
			var authentication = authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(email, request.password())
			);

			UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
			String token = jwtService.generateToken(principal.getId(), principal.getUsername());
			return AuthResponse.bearer(
					token,
					principal.getId(),
					principal.getUsername(),
					principal.getName()
			);
		}
		catch (org.springframework.security.core.AuthenticationException ex) {
			// Generic message avoids leaking whether email exists.
			throw new UnauthorizedException("Invalid email or password");
		}
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase();
	}
}
