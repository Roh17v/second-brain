package com.secondbrain.chat.dto;

import java.util.UUID;

public record CitationResponse(
		int index,
		UUID chunkId,
		UUID documentId,
		String sourceFilename,
		int chunkIndex,
		double score,
		String snippet
) {
}
