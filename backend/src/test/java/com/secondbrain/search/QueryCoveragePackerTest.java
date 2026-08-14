package com.secondbrain.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class QueryCoveragePackerTest {

	@Test
	void picksADistinctChunkPerQueryInsteadOfRepeatingHeaps() {
		UUID doc = UUID.randomUUID();
		SearchHitResponse heap = hit(doc, 72, "Heaps Time Complexity O(n log n) heapify");
		SearchHitResponse stack = hit(doc, 83, "Advantages of Stack O(1) time complexity push pop");
		SearchHitResponse intro = hit(doc, 1, "stacks queues trees time and space complexity");

		List<SearchHitResponse> packed = QueryCoveragePacker.pack(
				List.of("time complexity Array Stack Queue", "time complexity Stack", "time complexity Heap"),
				List.of(
						List.of(intro, heap),
						List.of(intro, stack),
						List.of(heap, intro)
				),
				List.of(heap, intro, stack),
				5
		);

		assertTrue(packed.size() >= 2);
		assertEquals(83, packed.get(0).chunkIndex());
		assertEquals(72, packed.get(1).chunkIndex());
	}

	private static SearchHitResponse hit(UUID doc, int index, String content) {
		return new SearchHitResponse(UUID.randomUUID(), doc, index, content, 0.5);
	}
}
