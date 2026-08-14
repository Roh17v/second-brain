package com.secondbrain.search;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * For multi-query retrieval, take the best unused hit for each query first
 * so one dense topic (heaps) cannot fill every slot.
 */
public final class QueryCoveragePacker {

	private QueryCoveragePacker() {
	}

	/**
	 * @param queries        search strings (same order as {@code perQueryHits})
	 * @param perQueryHits   ranked hits for each query
	 * @param filler         extra candidates (e.g. global RRF list)
	 * @param topK           final size
	 */
	public static List<SearchHitResponse> pack(
			List<String> queries,
			List<List<SearchHitResponse>> perQueryHits,
			List<SearchHitResponse> filler,
			int topK
	) {
		int k = topK <= 0 ? 5 : topK;
		if (perQueryHits == null || perQueryHits.isEmpty()) {
			return LexicalReranker.rerank(queries, filler == null ? List.of() : filler, k);
		}

		Set<UUID> seen = new LinkedHashSet<>();
		List<SearchHitResponse> picked = new ArrayList<>();

		int n = Math.min(queries == null ? 0 : queries.size(), perQueryHits.size());
		// Prefer per-topic queries (index 1+) so a combined primary query
		// does not spend the first slot on an intro/heap page.
		if (n > 1) {
			for (int i = 1; i < n && picked.size() < k; i++) {
				pickOne(queries.get(i), perQueryHits.get(i), seen, picked);
			}
			if (picked.size() < k) {
				pickOne(queries.getFirst(), perQueryHits.getFirst(), seen, picked);
			}
		}
		else if (n == 1) {
			pickOne(queries.getFirst(), perQueryHits.getFirst(), seen, picked);
		}

		if (filler != null) {
			for (SearchHitResponse hit : filler) {
				if (picked.size() >= k) {
					break;
				}
				if (hit != null && hit.chunkId() != null && seen.add(hit.chunkId())) {
					picked.add(hit);
				}
			}
		}
		return picked;
	}

	private static void pickOne(
			String query,
			List<SearchHitResponse> hits,
			Set<UUID> seen,
			List<SearchHitResponse> picked
	) {
		SearchHitResponse best = bestUnused(query, hits, seen);
		if (best != null) {
			picked.add(best);
			seen.add(best.chunkId());
		}
	}

	private static SearchHitResponse bestUnused(
			String query,
			List<SearchHitResponse> hits,
			Set<UUID> seen
	) {
		if (hits == null || hits.isEmpty()) {
			return null;
		}
		SearchHitResponse best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (SearchHitResponse hit : hits) {
			if (hit == null || hit.chunkId() == null || seen.contains(hit.chunkId())) {
				continue;
			}
			double s = LexicalScorer.score(query, hit.content());
			if (s > bestScore) {
				bestScore = s;
				best = hit;
			}
		}
		if (best != null) {
			return best;
		}
		for (SearchHitResponse hit : hits) {
			if (hit != null && hit.chunkId() != null && !seen.contains(hit.chunkId())) {
				return hit;
			}
		}
		return null;
	}
}
