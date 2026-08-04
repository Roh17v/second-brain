package com.secondbrain.chat.dto;

import java.util.List;

public record ConversationDetailResponse(
		ConversationResponse conversation,
		List<ChatMessageResponse> messages
) {
}
