package com.secondbrain.chat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
		@NotBlank(message = "message is required")
		@Size(max = 4000)
		String message,

		@Min(1)
		@Max(20)
		Integer topK
) {
}
