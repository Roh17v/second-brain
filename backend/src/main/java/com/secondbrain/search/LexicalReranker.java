package com.secondbrain.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Re-orders first-stage candidates by lexical overlap with the search text.
 * Prefers chunks that actually mention the asked terms (and Big-O when relevant)
 * over intro pages that only list topic names.
 */
public final class LexicalReranker {

	private LexicalReranker() {
	}

	public static List<SearchHitResponse> rerank(String query, List<SearchHitResponse> hits, int topK) {
		return rerank(query == null ? List.of() : List.of(query), hits, topK);
	}

	/**
	 * Score each hit against every query and keep the best. That way a stack
	 * follow-up query can lift the stack O(1) chunk without the intro page
	 * winning just because it lists every structure name.
	 */
	public static List<SearchHitResponse> rerank(
			List<String> queries,
			List<SearchHitResponse> hits,
			int topK
	) {
		if (hits == null || hits.isEmpty()) {
			return List.of();
		}
		int k = topK <= 0 ? 5 : topK;
		List<String> qs = queries == null
				? List.of()
				: queries.stream().filter(q -> q != null && !q.isBlank()).toList();
		List<Scored> scored = new ArrayList<>(hits.size());
		boolean anyPositive = false;
		for (int i = 0; i < hits.size(); i++) {
			SearchHitResponse hit = hits.get(i);
			double lexical = 0;
			for (String q : qs) {
				lexical = Math.max(lexical, LexicalScorer.score(q, hit.content()));
			}
			if (lexical > 0) {
				anyPositive = true;
			}
			scored.add(new Scored(hit, lexical, i));
		}
		if (!anyPositive) {
			return hits.size() <= k ? List.copyOf(hits) : List.copyOf(hits.subList(0, k));
		}
		scored.sort(Comparator
				.comparingDouble(Scored::lexical).reversed()
				.thenComparingInt(Scored::originalRank));
		List<SearchHitResponse> out = new ArrayList<>(Math.min(k, scored.size()));
		for (int i = 0; i < Math.min(k, scored.size()); i++) {
			Scored s = scored.get(i);
			SearchHitResponse h = s.hit();
			out.add(new SearchHitResponse(
					h.chunkId(),
					h.documentId(),
					h.chunkIndex(),
					h.content(),
					s.lexical()
			));
		}
		return out;
	}

	private record Scored(SearchHitResponse hit, double lexical, int originalRank) {
	}
}
