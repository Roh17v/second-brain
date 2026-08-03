package com.secondbrain.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkspaceRequest(
		@NotBlank(message = "name is required")
		@Size(max = 200)
		String name,

		@Size(max = 1000)
		String description
) {
}
