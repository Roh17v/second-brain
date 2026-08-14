package com.secondbrain.user.dto;

public record LibraryStatsResponse(
		long collections,
		long documents,
		long indexed,
		long chunks
) {
}
