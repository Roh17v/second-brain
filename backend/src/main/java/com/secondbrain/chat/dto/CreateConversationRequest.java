package com.secondbrain.chat.dto;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
		@Size(max = 300)
		String title
) {
}
