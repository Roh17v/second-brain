package com.secondbrain.chat.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.secondbrain.search.SearchHitResponse;

class RagPromptBuilderTest {

	@Test
	void truncatesLongChunksInPrompt() {
		RagPromptBuilder builder = new RagPromptBuilder();
		String longBody = "A".repeat(RagPromptBuilder.MAX_CONTEXT_CHUNK_CHARS + 500);
		SearchHitResponse hit = new SearchHitResponse(
				UUID.randomUUID(),
				UUID.randomUUID(),
				0,
				longBody,
				0.9
		);

		RagPromptBuilder.BuiltPrompt built = builder.build(
				"What is X?",
				List.of(hit),
				List.of("notes.pdf")
		);

		assertTrue(built.userPrompt().contains("Question: What is X?"));
		assertTrue(built.systemPrompt().toLowerCase().contains("ocr"));
		// Full long body should not appear untruncated in prompt
		assertFalse(built.userPrompt().contains(longBody));
		assertTrue(built.userPrompt().contains("..."));
	}

	@Test
	void includesResolvedQuestionForFollowUps() {
		RagPromptBuilder builder = new RagPromptBuilder();
		RagPromptBuilder.BuiltPrompt built = builder.build(
				"complexity for these",
				"What is the time and space complexity of each of: Array, Stack?",
				List.of(),
				List.of()
		);
		assertTrue(built.userPrompt().contains("User message: complexity for these"));
		assertTrue(built.userPrompt().contains("Resolved question"));
		assertTrue(built.userPrompt().contains("Array, Stack"));
		assertTrue(built.systemPrompt().toLowerCase().contains("resolved question"));
	}

	@Test
	void includesSectionHeadingInContextHeader() {
		RagPromptBuilder builder = new RagPromptBuilder();
		SearchHitResponse hit = new SearchHitResponse(
				UUID.randomUUID(),
				UUID.randomUUID(),
				7,
				"Section: Zookeeper - Best Solution\nRanges assigned to worker threads.",
				0.8
		);
		RagPromptBuilder.BuiltPrompt built = builder.build(
				"url shortener ids",
				List.of(hit),
				List.of("sd-notes.pdf")
		);
		assertTrue(built.userPrompt().contains("section=Zookeeper - Best Solution"));
	}
}
