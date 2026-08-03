package com.secondbrain.document.dto;

import java.time.Instant;
import java.util.UUID;

import com.secondbrain.document.entity.DocumentStatus;

public record DocumentResponse(
		UUID id,
		UUID workspaceId,
		UUID ownerId,
		String originalFilename,
		String contentType,
		long sizeBytes,
		DocumentStatus status,
		String failureReason,
		Instant createdAt,
		Instant updatedAt
) {
}
