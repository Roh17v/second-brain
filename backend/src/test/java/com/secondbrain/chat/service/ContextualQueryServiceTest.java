package com.secondbrain.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.secondbrain.ai.llm.LlmClient;
import com.secondbrain.ai.llm.LlmMessage;
import com.secondbrain.chat.entity.ChatMessage;
import com.secondbrain.chat.entity.MessageRole;

class ContextualQueryServiceTest {

	@Test
	void standaloneUsesOriginalWithoutCallingLlm() {
		RecordingLlm llm = new RecordingLlm("SHOULD_NOT_BE_USED");
		ContextualQueryService svc = new ContextualQueryService(llm);

		ContextualQueryResult r = svc.prepare(
				"What is Redis?",
				List.of(user("hi"), assistant("hello"))
		);

		assertEquals("What is Redis?", r.searchQuery());
		assertFalse(r.rewritten());
		assertEquals(ContextualQueryResult.METHOD_ORIGINAL, r.method());
		assertEquals(0, llm.calls);
	}

	@Test
	void followUpUsesLlmRewriteWhenValid() {
		RecordingLlm llm = new RecordingLlm(
				"time complexity of arrays linked lists stacks queues trees graphs hash tables"
		);
		ContextualQueryService svc = new ContextualQueryService(llm);

		ContextualQueryResult r = svc.prepare(
				"Give complexity for these",
				List.of(
						user("What are common non-primitive data structures?"),
						assistant("Arrays, linked lists, stacks, queues, trees, graphs, hash tables.")
				)
		);

		assertTrue(r.rewritten());
		// Multi-query promotion is expected when prior turn listed several structures
		assertTrue(
				r.method().equals(ContextualQueryResult.METHOD_LLM_REWRITE)
						|| r.method().equals(ContextualQueryResult.METHOD_MULTI_QUERY),
				r.method()
		);
		assertTrue(r.searchQuery().toLowerCase().contains("arrays")
				|| r.searchQuery().toLowerCase().contains("array"));
		assertEquals(1, llm.calls);
	}

	@Test
	void complexityForThese_mergesEntitiesEvenIfLlmOmitsThem() {
		// Weak rewrite — no structure names
		RecordingLlm llm = new RecordingLlm("time complexity of data structures");
		ContextualQueryService svc = new ContextualQueryService(llm);

		ContextualQueryResult r = svc.prepare(
				"complexity for these",
				List.of(
						user("most common data structures used in dsa?"),
						assistant("""
								The most common data structures are:

								Array
								Stack
								Queue
								Tree
								Graph
								Hash Table
								""")
				)
		);

		assertTrue(r.rewritten());
		String search = r.searchQuery().toLowerCase();
		assertTrue(search.contains("array"), search);
		assertTrue(search.contains("stack"), search);
		assertTrue(search.contains("queue"), search);
		assertTrue(search.contains("hash"), search);
		assertTrue(r.resolvedQuestion().toLowerCase().contains("complexity"));
		assertTrue(r.resolvedQuestion().contains("Array"));
		// Multi-query: combined + per-entity (LangChain MultiQuery style)
		assertTrue(r.multiQuery(), "expected multi-query plan, got " + r.searchQueries());
		assertTrue(r.searchQueries().size() >= 6);
		assertTrue(r.searchQueries().stream().anyMatch(q -> q.toLowerCase().contains("array")));
		assertEquals(ContextualQueryResult.METHOD_MULTI_QUERY, r.method());
	}

	@Test
	void complexityFollowUpUsesOriginalMarkdownListNotLastAnswerHeadings() {
		RecordingLlm llm = new RecordingLlm("time and space complexity of Binary Search and Heaps");
		ContextualQueryService svc = new ContextualQueryService(llm);

		ContextualQueryResult r = svc.prepare(
				"complexity for these?",
				List.of(
						user("most common and popular data structures used in dsa?"),
						assistant("""
								*   **Arrays**: Basic structures for storing collections of elements.
								*   **Stacks**: Store elements in a Last-In-First-Out (LIFO) order.
								*   **Queues**: Store elements in a First-In-First-Out (FIFO) order.
								"""),
						user("time complexity for these?"),
						assistant("""
								**1. Binary Search**
								*   **Time Complexity:** O(log n)
								**2. Heaps**
								Time Complexity: O(n log n)
								Space Complexity: O(n)
								""")
				)
		);

		assertTrue(r.multiQuery(), r.searchQueries().toString());
		assertTrue(r.searchQueries().stream().anyMatch(q -> q.toLowerCase().contains("stack")), r.searchQueries().toString());
		assertTrue(r.searchQueries().stream().noneMatch(q -> q.contains("**")), r.searchQueries().toString());
	}

	@Test
	void buildMultiQueries_onePerEntityPlusPrimary() {
		List<String> qs = ContextualQueryService.buildMultiQueries(
				"complexity for these",
				"time complexity Array Stack",
				List.of("Array", "Stack", "Queue")
		);
		assertTrue(qs.size() >= 4);
		assertTrue(qs.getFirst().contains("Array"));
		assertTrue(qs.stream().anyMatch(q -> q.equals("time complexity space complexity Array")
				|| q.toLowerCase().contains("array")));
	}

	@Test
	void invalidRewriteFallsBackButStillCarriesEntities() {
		RecordingLlm llm = new RecordingLlm("Echo answer based on retrieved context for: foo");
		ContextualQueryService svc = new ContextualQueryService(llm);

		ContextualQueryResult r = svc.prepare(
				"complexity for these",
				List.of(
						user("List data structures"),
						assistant("""
								Array
								Stack
								""")
				)
		);

		assertTrue(r.rewritten());
		assertTrue(r.searchQuery().toLowerCase().contains("array"));
		assertTrue(r.searchQuery().toLowerCase().contains("stack"));
		assertTrue(r.searchQuery().toLowerCase().contains("complex")
				|| r.resolvedQuestion().toLowerCase().contains("complex"));
	}

	@Test
	void longCapQuestionDoesNotInheritPriorCapacityMetrics() {
		RecordingLlm llm = new RecordingLlm("SHOULD_NOT_BE_USED");
		ContextualQueryService svc = new ContextualQueryService(llm);

		ContextualQueryResult r = svc.prepare(
				"explain me cap theoram in easy language i understand consistency and availabity "
						+ "but partition tolerance is a little difficult give me an example while explaining it",
				List.of(
						user("capacity estimation of facebook?"),
						assistant("""
								1. Traffic Estimation
								Total Users: 1 Billion.
								Query Load: 7 queries per day.
								QPS: 18,000
								Daily Storage: 250 GB/day
								Total RAM: 750 GB
								Total Servers: 180
								""")
				)
		);

		assertFalse(r.rewritten(), r.method());
		assertEquals(0, llm.calls);
		assertEquals(1, r.searchQueries().size());
		String search = r.searchQuery().toLowerCase();
		assertTrue(search.contains("cap") || search.contains("partition"), search);
		assertFalse(search.contains("total users"), search);
		assertFalse(search.contains("qps"), search);
		assertFalse(r.resolvedQuestion().toLowerCase().contains("total users"));
	}

	@Test
	void consistencyDoesNotBecomeProsConsPrefix() {
		assertFalse(ContextualQueryService.attributePrefix(
				"explain consistency and partition tolerance"
		).contains("pros"));
		assertTrue(ContextualQueryService.attributePrefix("pros and cons of redis")
				.contains("pros"));
	}

	@Test
	void sanitizeRejectsLongAnswers() {
		String longText = "x".repeat(600);
		assertEquals(null, ContextualQueryService.sanitizeRewrite(longText, "q"));
	}

	@Test
	void sanitizeKeepsCleanQuery() {
		assertEquals(
				"time complexity of stacks",
				ContextualQueryService.sanitizeRewrite("time complexity of stacks", "these")
		);
	}

	private static ChatMessage user(String text) {
		return new ChatMessage(UUID.randomUUID(), MessageRole.USER, text);
	}

	private static ChatMessage assistant(String text) {
		return new ChatMessage(UUID.randomUUID(), MessageRole.ASSISTANT, text);
	}

	private static final class RecordingLlm implements LlmClient {
		private final String response;
		int calls;

		RecordingLlm(String response) {
			this.response = response;
		}

		@Override
		public String chat(String systemPrompt, List<LlmMessage> messages) {
			calls++;
			return response;
		}

		@Override
		public void streamChat(String systemPrompt, List<LlmMessage> messages, Consumer<String> onToken) {
			onToken.accept(chat(systemPrompt, messages));
		}

		@Override
		public String modelId() {
			return "test-llm";
		}
	}
}
