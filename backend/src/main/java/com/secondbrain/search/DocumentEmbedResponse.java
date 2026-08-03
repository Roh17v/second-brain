package com.secondbrain.search;

import com.secondbrain.document.dto.DocumentResponse;

public record DocumentEmbedResponse(
		DocumentResponse document,
		int embeddingsCreated,
		int embeddingsSkipped,
		long totalEmbeddedChunks,
		String model
) {
}
