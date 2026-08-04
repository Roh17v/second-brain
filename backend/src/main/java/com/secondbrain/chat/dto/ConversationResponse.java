package com.secondbrain.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
		UUID id,
		UUID workspaceId,
		String title,
		Instant createdAt,
		Instant updatedAt
) {
}
