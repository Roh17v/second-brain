package com.secondbrain.search.rerank;

import java.util.List;

import com.secondbrain.search.SearchHitResponse;

/** Pass-through: keep first-stage order and trim to top-K. */
public class IdentityChunkReranker implements ChunkReranker {

	public static final String ID = "identity";

	@Override
	public List<SearchHitResponse> rerank(
			List<String> queries,
			List<SearchHitResponse> candidates,
			int topK
	) {
		if (candidates == null || candidates.isEmpty()) {
			return List.of();
		}
		int k = topK <= 0 ? 5 : topK;
		return candidates.size() <= k ? List.copyOf(candidates) : List.copyOf(candidates.subList(0, k));
	}

	@Override
	public String modelId() {
		return ID;
	}
}
