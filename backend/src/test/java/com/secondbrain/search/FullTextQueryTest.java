package com.secondbrain.search;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FullTextQueryTest {

	@Test
	void orsTokensSoSpaceIsNotRequiredWithStack() {
		String q = FullTextQuery.orTsQuery("time complexity space complexity Stack");
		assertTrue(q.contains(" | "));
		assertTrue(q.contains("time"));
		assertTrue(q.contains("stack"));
		assertFalse(q.contains(" & "));
	}

	@Test
	void blankOrStopwordsOnlyYieldsEmpty() {
		assertTrue(FullTextQuery.orTsQuery("the and of").isEmpty());
		assertTrue(FullTextQuery.orTsQuery("   ").isEmpty());
	}
}
