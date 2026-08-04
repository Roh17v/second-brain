package com.secondbrain.chat.dto;

import java.util.List;
import java.util.UUID;

public record ChatAnswerResponse(
		UUID conversationId,
		ChatMessageResponse userMessage,
		ChatMessageResponse assistantMessage,
		List<CitationResponse> citations,
		String model
) {
}
