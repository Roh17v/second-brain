package com.secondbrain.search;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LexicalScorerTest {

	@Test
	void ranksExactComplexityLineAboveUnrelatedHeapChapter() {
		String query = "time complexity stack O(1)";
		String stack = "Stack operations: push and pop are O(1) time complexity.";
		String heap = "Heaps are used for priority queues. Heapify walks the tree in O(n) time.";

		double stackScore = LexicalScorer.score(query, stack);
		double heapScore = LexicalScorer.score(query, heap);

		assertTrue(stackScore > 0, "stack line should match");
		assertTrue(stackScore > heapScore, "stack O(1) should outrank heap chapter");
	}

	@Test
	void bigOLineBeatsIntroThatOnlyListsNames() {
		String query = "time complexity Stack";
		String intro = "stacks, queues, trees. choice depends on time and space complexity.";
		String fact = "Advantages of Stack. addition and removal of elements (O(1) time complexity).";
		assertTrue(LexicalScorer.score(query, fact) > LexicalScorer.score(query, intro));
	}

	@Test
	void noOverlapScoresZero() {
		assertTrue(LexicalScorer.score("JWT refresh token", "binary search trees") == 0);
	}

	@Test
	void blankQueryScoresZero() {
		assertTrue(LexicalScorer.score("   ", "Stack O(1)") == 0);
		assertTrue(LexicalScorer.score(null, "Stack O(1)") == 0);
	}
}
