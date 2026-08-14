package com.secondbrain.chat.service;

import java.util.List;

/**
 * Outcome of conversation-aware search query preparation.
 * <ul>
 *   <li>{@link #searchQuery()} — primary query (logs / single-query fallback)</li>
 *   <li>{@link #searchQueries()} — one or more queries for multi-query + RRF retrieval</li>
 *   <li>{@link #resolvedQuestion()} — expanded wording for the answer LLM</li>
 *   <li>{@link #originalQuery()} — raw user text (UI / logs)</li>
 * </ul>
 */
public record ContextualQueryResult(
		String originalQuery,
		String searchQuery,
		List<String> searchQueries,
		String resolvedQuestion,
		boolean rewritten,
		String method
) {
	public static final String METHOD_ORIGINAL = "original";
	public static final String METHOD_LLM_REWRITE = "llm_rewrite";
	public static final String METHOD_HISTORY_CONCAT = "history_concat";
	public static final String METHOD_REWRITE_FALLBACK_CONCAT = "rewrite_fallback_concat";
	public static final String METHOD_ENTITY_EXPAND = "entity_expand";
	public static final String METHOD_MULTI_QUERY = "multi_query";

	public ContextualQueryResult {
		if (searchQueries == null || searchQueries.isEmpty()) {
			searchQueries = List.of(searchQuery == null ? "" : searchQuery);
		}
		else {
			searchQueries = List.copyOf(searchQueries);
		}
		if (resolvedQuestion == null || resolvedQuestion.isBlank()) {
			resolvedQuestion = originalQuery;
		}
	}

	/** Convenience: single search query. */
	public ContextualQueryResult(
			String originalQuery,
			String searchQuery,
			String resolvedQuestion,
			boolean rewritten,
			String method
	) {
		this(originalQuery, searchQuery, List.of(searchQuery), resolvedQuestion, rewritten, method);
	}

	public ContextualQueryResult(
			String originalQuery,
			String searchQuery,
			boolean rewritten,
			String method
	) {
		this(originalQuery, searchQuery, List.of(searchQuery), originalQuery, rewritten, method);
	}

	public boolean multiQuery() {
		return searchQueries != null && searchQueries.size() > 1;
	}
}
