package com.secondbrain.document.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentChunkResponse(
		UUID id,
		UUID documentId,
		int chunkIndex,
		String content,
		int contentLength,
		String sectionHeading,
		Instant createdAt
) {
}
