package com.secondbrain.ai.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmbeddingTextsTest {

	@Test
	void nomicAddsDocumentAndQueryPrefixes() {
		assertEquals(
				"search_document: hello",
				EmbeddingTexts.prepare("nomic-embed-text", "hello", EmbeddingTask.DOCUMENT)
		);
		assertEquals(
				"search_query: hello",
				EmbeddingTexts.prepare("nomic-embed-text", "hello", EmbeddingTask.QUERY)
		);
	}

	@Test
	void nonNomicLeavesTextUnchanged() {
		assertEquals("hello", EmbeddingTexts.prepare("mxbai-embed-large", "hello", EmbeddingTask.QUERY));
	}

	@Test
	void doesNotDoublePrefix() {
		String already = "search_query: hello";
		assertEquals(already, EmbeddingTexts.prepare("nomic-embed-text", already, EmbeddingTask.QUERY));
	}

	@Test
	void detectsNomicModelIds() {
		assertTrue(EmbeddingTexts.isNomic("nomic-embed-text"));
		assertFalse(EmbeddingTexts.isNomic("gemini-embedding-2"));
	}
}
