package com.secondbrain.search.rerank;

import java.util.List;

import com.secondbrain.search.LexicalReranker;
import com.secondbrain.search.SearchHitResponse;

/**
 * Default reranker: token overlap + Big-O bonus. No extra model or API.
 * Swap via {@code app.search.rerank-provider} when a cross-encoder is added.
 */
public class LexicalChunkReranker implements ChunkReranker {

	public static final String ID = "lexical";

	@Override
	public List<SearchHitResponse> rerank(
			List<String> queries,
			List<SearchHitResponse> candidates,
			int topK
	) {
		return LexicalReranker.rerank(queries, candidates, topK);
	}

	@Override
	public String modelId() {
		return ID;
	}
}
