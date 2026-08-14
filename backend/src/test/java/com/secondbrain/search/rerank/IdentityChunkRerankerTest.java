package com.secondbrain.search.rerank;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.secondbrain.search.SearchHitResponse;

class IdentityChunkRerankerTest {

	@Test
	void trimsToTopKAndKeepsOrder() {
		UUID doc = UUID.randomUUID();
		SearchHitResponse a = new SearchHitResponse(UUID.randomUUID(), doc, 0, "a", 0.9);
		SearchHitResponse b = new SearchHitResponse(UUID.randomUUID(), doc, 1, "b", 0.8);
		SearchHitResponse c = new SearchHitResponse(UUID.randomUUID(), doc, 2, "c", 0.7);

		List<SearchHitResponse> out = new IdentityChunkReranker().rerank(
				List.of("q"),
				List.of(a, b, c),
				2
		);

		assertEquals(2, out.size());
		assertEquals(a.chunkId(), out.getFirst().chunkId());
		assertEquals(b.chunkId(), out.get(1).chunkId());
	}
}
