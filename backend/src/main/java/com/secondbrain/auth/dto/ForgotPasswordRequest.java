package com.secondbrain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(
		@NotBlank(message = "email is required")
		@Email(message = "email must be valid")
		@Size(max = 320)
		String email
) {
}
