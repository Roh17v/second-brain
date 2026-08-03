package com.secondbrain.workspace.dto;

import jakarta.validation.constraints.Size;

public record UpdateWorkspaceRequest(
		@Size(max = 200)
		String name,

		@Size(max = 1000)
		String description
) {
}
