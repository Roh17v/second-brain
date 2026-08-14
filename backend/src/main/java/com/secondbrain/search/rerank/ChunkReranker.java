package com.secondbrain.search.rerank;

import java.util.List;

import com.secondbrain.search.SearchHitResponse;

/**
 * Second-stage ranker: scores query + chunk together and keeps the best hits.
 * First-stage retrieval should pass a wider candidate pool.
 */
public interface ChunkReranker {

	/**
	 * @param queries    the search strings used for retrieval (one or more)
	 * @param candidates first-stage hits, best-first
	 * @param topK       how many to keep for the prompt
	 */
	List<SearchHitResponse> rerank(List<String> queries, List<SearchHitResponse> candidates, int topK);

	/** Stable id for logs (e.g. {@code lexical}, {@code identity}). */
	String modelId();
}
