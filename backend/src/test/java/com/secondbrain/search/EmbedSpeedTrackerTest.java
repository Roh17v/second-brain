package com.secondbrain.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.secondbrain.ai.embedding.EmbeddingProperties;

class EmbedSpeedTrackerTest {

	@Test
	void startsFromConfigAndEasesTowardSamples() {
		EmbeddingProperties props = new EmbeddingProperties();
		props.setEstimatedMsPerChunk(4500);
		EmbedSpeedTracker tracker = new EmbedSpeedTracker(props);

		assertEquals(4500, tracker.averageMs());
		tracker.record(1500);
		assertTrue(tracker.averageMs() < 4500);
		assertTrue(tracker.averageMs() > 1500);
	}
}
