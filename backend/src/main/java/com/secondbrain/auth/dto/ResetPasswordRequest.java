package com.secondbrain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
		@NotBlank(message = "email is required")
		@Email(message = "email must be valid")
		@Size(max = 320)
		String email,

		@NotBlank(message = "code is required")
		@Size(min = 6, max = 6, message = "code must be 6 digits")
		String code,

		@NotBlank(message = "password is required")
		@Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
		String newPassword
) {
}
