package com.secondbrain.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
import com.secondbrain.auth.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request);
	}

	@PostMapping("/verify-email")
	public AuthResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
		return authService.verifyEmail(request);
	}

	@PostMapping("/resend-verification")
	public MessageResponse resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
		return authService.resendVerification(request);
	}

	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	/**
	 * Request OTP to set or reset password (forgot password + Google users setting a password).
	 */
	@PostMapping("/forgot-password")
	public ForgotPasswordResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		return authService.forgotPassword(request);
	}

	/**
	 * Confirm OTP and set a new password; returns a session JWT.
	 */
	@PostMapping("/reset-password")
	public AuthResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		return authService.resetPassword(request);
	}

	/**
	 * Continue with Google: body is a GIS ID token from the browser.
	 * Creates, links, or signs in the matching account and returns our JWT.
	 */
	@PostMapping("/google")
	public AuthResponse google(@Valid @RequestBody GoogleAuthRequest request) {
		return authService.loginWithGoogle(request);
	}
}
