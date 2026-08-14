package com.secondbrain.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LexicalRerankerTest {

	@Test
	void prefersStackBigOOverIntroList() {
		UUID doc = UUID.randomUUID();
		UUID introId = UUID.randomUUID();
		UUID stackId = UUID.randomUUID();
		SearchHitResponse intro = new SearchHitResponse(
				introId,
				doc,
				1,
				"stacks, queues, trees, graphs and hash tables. The choice depends on time and space complexity.",
				0.9
		);
		SearchHitResponse stack = new SearchHitResponse(
				stackId,
				doc,
				83,
				"Advantages of Stack. Supports efficient addition and removal of elements (O(1) time complexity).",
				0.2
		);

		List<SearchHitResponse> ranked = LexicalReranker.rerank(
				"time complexity Stack",
				List.of(intro, stack),
				5
		);

		assertEquals(stackId, ranked.getFirst().chunkId());
	}

	@Test
	void maxAcrossQueriesDoesNotLetIntroWinEveryEntity() {
		UUID doc = UUID.randomUUID();
		UUID introId = UUID.randomUUID();
		UUID stackId = UUID.randomUUID();
		SearchHitResponse intro = new SearchHitResponse(
				introId,
				doc,
				1,
				"Array Stack Queue Tree Graph Hash Table. time and space complexity.",
				0.9
		);
		SearchHitResponse stack = new SearchHitResponse(
				stackId,
				doc,
				83,
				"Advantages of Stack. Supports efficient addition and removal (O(1) time complexity).",
				0.2
		);

		List<SearchHitResponse> ranked = LexicalReranker.rerank(
				List.of("time complexity Array", "time complexity Stack", "time complexity Queue"),
				List.of(intro, stack),
				5
		);

		assertEquals(stackId, ranked.getFirst().chunkId());
	}
}
