package com.secondbrain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(
		@NotBlank @Email @Size(max = 320) String email,
		@NotBlank
		@Pattern(regexp = "\\d{6}", message = "code must be 6 digits")
		String code
) {
}
