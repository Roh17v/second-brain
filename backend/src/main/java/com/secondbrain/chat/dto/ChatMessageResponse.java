package com.secondbrain.chat.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.secondbrain.chat.entity.MessageRole;

public record ChatMessageResponse(
		UUID id,
		MessageRole role,
		String content,
		Instant createdAt,
		List<CitationResponse> citations
) {
}
