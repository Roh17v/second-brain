package com.secondbrain.workspace.dto;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceResponse(
		UUID id,
		String name,
		String description,
		UUID ownerId,
		Instant createdAt,
		Instant updatedAt
) {
}
