package com.secondbrain.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reciprocal Rank Fusion (RRF) — same fusion used by multi-query retrievers
 * (LangChain MultiQueryRetriever, many hybrid search stacks).
 * <p>
 * For each ranked list, document d at 1-based rank r contributes {@code 1 / (k + r)}.
 * Scores are summed across lists; higher = better.
 *
 * @see <a href="https://plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf">Cormack et al.</a>
 */
public final class ReciprocalRankFusion {

	/** Standard constant from the RRF paper / OpenSearch defaults. */
	public static final int DEFAULT_K = 60;

	private ReciprocalRankFusion() {
	}

	/**
	 * @param rankedLists each list is best-first (index 0 = rank 1)
	 * @param topK        final number of hits to return
	 * @return fused hits; {@link SearchHitResponse#score()} is the RRF score
	 */
	public static List<SearchHitResponse> fuse(List<List<SearchHitResponse>> rankedLists, int topK) {
		return fuse(rankedLists, topK, DEFAULT_K);
	}

	public static List<SearchHitResponse> fuse(List<List<SearchHitResponse>> rankedLists, int topK, int rrfK) {
		if (rankedLists == null || rankedLists.isEmpty()) {
			return List.of();
		}
		int k = topK <= 0 ? 5 : topK;
		int constant = rrfK <= 0 ? DEFAULT_K : rrfK;

		Map<UUID, Double> rrfScores = new HashMap<>();
		Map<UUID, SearchHitResponse> exemplars = new HashMap<>();

		for (List<SearchHitResponse> list : rankedLists) {
			if (list == null || list.isEmpty()) {
				continue;
			}
			for (int i = 0; i < list.size(); i++) {
				SearchHitResponse hit = list.get(i);
				if (hit == null || hit.chunkId() == null) {
					continue;
				}
				int rank = i + 1; // 1-based
				double contrib = 1.0 / (constant + rank);
				rrfScores.merge(hit.chunkId(), contrib, Double::sum);
				// Keep the exemplar with highest original similarity for content
				exemplars.merge(hit.chunkId(), hit, (a, b) -> a.score() >= b.score() ? a : b);
			}
		}

		List<Map.Entry<UUID, Double>> ranked = new ArrayList<>(rrfScores.entrySet());
		ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

		List<SearchHitResponse> out = new ArrayList<>();
		for (int i = 0; i < Math.min(k, ranked.size()); i++) {
			Map.Entry<UUID, Double> e = ranked.get(i);
			SearchHitResponse base = exemplars.get(e.getKey());
			out.add(new SearchHitResponse(
					base.chunkId(),
					base.documentId(),
					base.chunkIndex(),
					base.content(),
					e.getValue()
			));
		}
		return out;
	}
}
