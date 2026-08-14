package com.secondbrain.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ReciprocalRankFusionTest {

	@Test
	void prefersChunksThatRankHighInMultipleLists() {
		UUID shared = UUID.randomUUID();
		UUID onlyA = UUID.randomUUID();
		UUID onlyB = UUID.randomUUID();
		UUID doc = UUID.randomUUID();

		SearchHitResponse s = hit(shared, doc, 0, 0.5);
		SearchHitResponse a = hit(onlyA, doc, 1, 0.9);
		SearchHitResponse b = hit(onlyB, doc, 2, 0.9);

		// shared is rank 2 in both lists; onlyA/onlyB are rank 1 in one list
		List<SearchHitResponse> fused = ReciprocalRankFusion.fuse(
				List.of(
						List.of(a, s),
						List.of(b, s)
				),
				3
		);

		assertEquals(3, fused.size());
		// shared appears twice → higher RRF than a single rank-1
		assertEquals(shared, fused.getFirst().chunkId());
		assertTrue(fused.getFirst().score() > fused.get(1).score());
	}

	@Test
	void emptyInputReturnsEmpty() {
		assertTrue(ReciprocalRankFusion.fuse(List.of(), 5).isEmpty());
	}

	@Test
	void emptyKeywordListDoesNotChangeDenseOrder() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID doc = UUID.randomUUID();
		List<SearchHitResponse> dense = List.of(hit(a, doc, 0, 0.9), hit(b, doc, 1, 0.4));

		List<SearchHitResponse> fused = ReciprocalRankFusion.fuse(List.of(dense, List.of()), 5);

		assertEquals(2, fused.size());
		assertEquals(a, fused.getFirst().chunkId());
	}

	private static SearchHitResponse hit(UUID chunkId, UUID docId, int idx, double score) {
		return new SearchHitResponse(chunkId, docId, idx, "content-" + idx, score);
	}
}
