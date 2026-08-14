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
	void headingAwareSplitsKeepSectionPrefix() {
		String text = """
				# Design URL Shortening Service

				Back of the envelope: 62^7 unique codes.

				# Design Rate Limiter

				Sliding window counter stores denied requests.
				""";
		List<StructuredChunk> chunks = chunker.chunkDocument(text);
		assertTrue(chunks.size() >= 2, "expected a chunk per heading");
		assertTrue(chunks.stream().anyMatch(c ->
				"Design URL Shortening Service".equals(c.sectionHeading())
						&& c.content().startsWith("Section: Design URL Shortening Service")
						&& c.content().contains("62^7")));
		assertTrue(chunks.stream().anyMatch(c ->
				"Design Rate Limiter".equals(c.sectionHeading())
						&& c.content().contains("Sliding window")));
	}

	@Test
	void stepHeadingsStayInsideParentSection() {
		String text = """
				# Design URL shortening Service

				Overview of the shortener.

				# ① Requirement Analysis

				How short should the URL be?

				# ② Ticket Server

				Central ID issuer is a single point of failure.

				# Design Rate Limiters

				Sliding window counter.
				""";
		List<StructuredChunk> chunks = chunker.chunkDocument(text);
		assertTrue(chunks.stream().anyMatch(c ->
				c.sectionHeading() != null
						&& c.sectionHeading().contains("URL shortening")
						&& c.content().contains("Ticket Server")
						&& c.content().contains("Requirement Analysis")));
		assertTrue(chunks.stream().noneMatch(c ->
				c.sectionHeading() != null && c.sectionHeading().contains("Ticket Server")));
		assertTrue(chunks.stream().anyMatch(c ->
				c.sectionHeading() != null
						&& c.sectionHeading().contains("Rate Limiters")
						&& c.content().contains("Sliding window")));
	}

	@Test
	void headingOnlySectionMergesIntoNext() {
		String text = """
				# Design URL shortening Service

				# Design Rate Limiters

				Body of the rate limiter only.
				""";
		List<StructuredChunk> chunks = chunker.chunkDocument(text);
		assertTrue(chunks.stream().anyMatch(c ->
				c.content().contains("Body of the rate limiter")
						&& c.content().contains("Design URL shortening Service")));
		assertTrue(chunks.stream().noneMatch(c ->
				c.sectionHeading() != null
						&& c.sectionHeading().contains("URL shortening")
						&& !c.content().contains("rate limiter")));
	}

	@Test
	void unitHeadingsAreDetected() {
		String text = """
				Unit-1
				Introduction to arrays and memory.

				Unit-2
				Stacks and queues.
				""";
		List<StructuredChunk> chunks = chunker.chunkDocument(text);
		assertTrue(chunks.size() >= 2);
		assertTrue(chunks.stream().anyMatch(c -> c.sectionHeading() != null && c.sectionHeading().startsWith("Unit-1")));
		assertTrue(chunks.stream().anyMatch(c -> c.sectionHeading() != null && c.sectionHeading().startsWith("Unit-2")));
	}

	@Test
	void noHeadingsFallsBackToCharacterWindows() {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < 40; i++) {
			builder.append("Paragraph ").append(i).append(" has no markdown heading at all.\n\n");
		}
		List<StructuredChunk> chunks = chunker.chunkDocument(builder.toString(), 200, 40);
		assertTrue(chunks.size() > 1);
		assertTrue(chunks.stream().allMatch(c -> c.sectionHeading() == null));
		assertFalse(chunks.getFirst().content().startsWith("Section: "));
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
