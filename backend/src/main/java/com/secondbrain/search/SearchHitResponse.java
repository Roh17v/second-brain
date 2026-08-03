package com.secondbrain.search;

import java.util.UUID;

public record SearchHitResponse(
		UUID chunkId,
		UUID documentId,
		int chunkIndex,
		String content,
		double score
) {
}
