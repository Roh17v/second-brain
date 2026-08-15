package com.secondbrain.document.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IngestEtaTest {

	@Test
	void unknownUntilChunked() {
		assertNull(IngestEta.remainingSeconds(null, 0, 4500));
		assertNull(IngestEta.remainingSeconds(0, 0, 4500));
	}

	@Test
	void remainingUsesChunkTimesAverage() {
		// 182 chunks × 4500 ms = 819 s
		assertEquals(819, IngestEta.remainingSeconds(182, 0, 4500));
		assertEquals(450, IngestEta.remainingSeconds(182, 82, 4500));
		assertEquals(0, IngestEta.remainingSeconds(182, 182, 4500));
	}

	@Test
	void emailOnlyWhenOverOneMinute() {
		assertFalse(IngestEta.shouldNotify(13, 4500)); // 58.5s
		assertTrue(IngestEta.shouldNotify(14, 4500)); // 63s
		assertFalse(IngestEta.shouldNotify(60));
		assertTrue(IngestEta.shouldNotify(61));
		assertFalse(IngestEta.shouldNotify((Integer) null));
	}
}
