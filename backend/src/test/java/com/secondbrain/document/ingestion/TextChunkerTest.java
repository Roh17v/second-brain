package com.secondbrain.document.ingestion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TextChunkerTest {

	private final TextChunker chunker = new TextChunker();

	@Test
	void shortTextIsSingleChunk() {
		List<String> chunks = chunker.chunk("Hello SecondBrain");
		assertTrue(chunks.size() == 1);
		assertTrue(chunks.getFirst().contains("Hello"));
	}

	@Test
	void longTextProducesMultipleChunksWithOverlapBehavior() {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < 50; i++) {
			builder.append("Paragraph ").append(i).append(" explains system design concepts in detail.\n\n");
		}
		List<String> chunks = chunker.chunk(builder.toString(), 200, 40);
		assertTrue(chunks.size() > 1);
		assertFalse(chunks.stream().anyMatch(String::isBlank));
		assertTrue(chunks.stream().allMatch(c -> c.length() <= 250));
	}
}
