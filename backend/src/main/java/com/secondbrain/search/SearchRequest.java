package com.secondbrain.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SearchRequest(
		@NotBlank(message = "query is required")
		@Size(max = 2000)
		String query,

		@Min(1)
		@Max(50)
		Integer topK
) {
}
