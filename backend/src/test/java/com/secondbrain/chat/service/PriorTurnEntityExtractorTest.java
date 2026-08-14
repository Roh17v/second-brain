package com.secondbrain.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.secondbrain.chat.entity.ChatMessage;
import com.secondbrain.chat.entity.MessageRole;

class PriorTurnEntityExtractorTest {

	@Test
	void extractsNewlineListFromAssistantStyleAnswer() {
		String answer = """
				Based on the provided notes, the most common data structures used in DSA are:

				Array
				Stack
				Queue
				Tree
				Graph
				Hash Table

				These structures are frequently cited as examples.
				""";
		List<String> entities = PriorTurnEntityExtractor.extractFromText(answer);
		assertTrue(entities.contains("Array"));
		assertTrue(entities.contains("Stack"));
		assertTrue(entities.contains("Queue"));
		assertTrue(entities.contains("Tree"));
		assertTrue(entities.contains("Graph"));
		assertTrue(entities.contains("Hash Table"));
		assertEquals(6, entities.size());
	}

	@Test
	void extractsBulletList() {
		List<String> entities = PriorTurnEntityExtractor.extractFromText("""
				Common structures:
				- Array
				- Linked List
				- Hash Map
				""");
		assertEquals(List.of("Array", "Linked List", "Hash Map"), entities);
	}

	@Test
	void mergeAppendsMissingEntities() {
		String merged = PriorTurnEntityExtractor.mergeEntitiesIntoQuery(
				"time complexity",
				List.of("Array", "Stack")
		);
		assertTrue(merged.toLowerCase().contains("array"));
		assertTrue(merged.toLowerCase().contains("stack"));
		assertTrue(merged.toLowerCase().contains("complexity"));
	}

	@Test
	void resolvedQuestionForComplexity() {
		String q = PriorTurnEntityExtractor.buildResolvedQuestion(
				"complexity for these",
				List.of("Array", "Stack", "Queue")
		);
		assertTrue(q.toLowerCase().contains("complexity"));
		assertTrue(q.contains("Array"));
		assertTrue(q.contains("Stack"));
		assertTrue(q.contains("Queue"));
	}

	@Test
	void extractFromPriorUsesLastAssistant() {
		List<ChatMessage> prior = List.of(
				new ChatMessage(UUID.randomUUID(), MessageRole.USER, "list them"),
				new ChatMessage(UUID.randomUUID(), MessageRole.ASSISTANT, """
						Array
						Stack
						""")
		);
		List<String> entities = PriorTurnEntityExtractor.extractFromPrior(prior);
		assertTrue(entities.contains("Array"));
		assertTrue(entities.contains("Stack"));
	}

	@Test
	void extractsLabeledDefinitionLinesFromProseAnswer() {
		String answer = """
				Based on your notes, the most popular and common non-primitive data structures used in DSA are:

				Arrays: Used for storing data, with dynamic arrays allowing for more efficient memory usage [2][3].
				Linked Lists: Can be used to implement other structures, such as queues [1][3].
				Stacks: Store elements in a Last-In-First-Out (LIFO) order [3][4].
				Queues: Store elements in a First-In-First-Out (FIFO) order [1][3][4].
				Trees: Hierarchical structures used for organizing data [3].
				Graphs: Used for representing networks [3][4].
				Hash Tables: Implement associative arrays that store key-value pairs [2][3][4].

				The choice of which data structure to use depends on efficiency requirements.
				""";
		List<String> entities = PriorTurnEntityExtractor.extractFromText(answer);
		assertTrue(entities.contains("Arrays"), entities.toString());
		assertTrue(entities.contains("Stacks"), entities.toString());
		assertTrue(entities.contains("Queues"), entities.toString());
		assertTrue(entities.contains("Trees"), entities.toString());
		assertTrue(entities.contains("Graphs"), entities.toString());
		assertTrue(entities.contains("Hash Tables"), entities.toString());
		assertTrue(entities.contains("Linked Lists"), entities.toString());
		assertEquals(7, entities.size(), entities.toString());
	}

	@Test
	void extractsLabeledLinesFromLatestUserChatStyle() {
		String answer = """
				Based on the provided notes, the most common and popular non-primitive data structures used in Data Structures and Algorithms (DSA) are:

				Arrays: Basic structures for storing collections of elements.
				Linked Lists: Used for organizing elements, such as in queue implementations [1].
				Stacks: Store elements in a Last-In-First-Out (LIFO) order. Common applications include undo-redo functionality, recursive function calls, and expression evaluation [4].
				Queues: Store elements in a First-In-First-Out (FIFO) order.
				Trees: Complex structures built from primitive types [3].
				Graphs: Used for path algorithms and traversal [4].
				Hash Tables: Implement associative arrays to store key-value pairs, providing fast access to data elements based on their keys [4].
				These structures are chosen based on the type and amount of data.
				""";
		List<String> entities = PriorTurnEntityExtractor.extractFromText(answer);
		assertTrue(entities.contains("Stacks"), entities.toString());
		assertTrue(entities.contains("Queues"), entities.toString());
		assertTrue(entities.contains("Hash Tables"), entities.toString());
	}

	@Test
	void ignoresTimeComplexityHeadings() {
		List<String> entities = PriorTurnEntityExtractor.extractFromText("""
				**Time Complexity:** O(log n)
				**Space Complexity:** O(1)
				""");
		assertTrue(entities.isEmpty(), entities.toString());
	}

	@Test
	void followUpSkipsComplexityBreakdownAndKeepsOriginalList() {
		List<ChatMessage> prior = List.of(
				new ChatMessage(UUID.randomUUID(), MessageRole.USER, "most common data structures?"),
				new ChatMessage(UUID.randomUUID(), MessageRole.ASSISTANT, """
						Arrays: Basic structures for storing collections of elements.
						Stacks: Store elements in LIFO order.
						Queues: Store elements in FIFO order.
						"""),
				new ChatMessage(UUID.randomUUID(), MessageRole.USER, "time complexity for these?"),
				new ChatMessage(UUID.randomUUID(), MessageRole.ASSISTANT, """
						1. Binary Search
						**Time Complexity:** O(log n)
						2. Heaps
						Time Complexity: O(n log n)
						Space Complexity: O(n)
						""")
		);
		List<String> entities = PriorTurnEntityExtractor.extractFromPrior(prior);
		assertTrue(entities.contains("Arrays"), entities.toString());
		assertTrue(entities.contains("Stacks"), entities.toString());
		assertTrue(entities.contains("Queues"), entities.toString());
		assertTrue(entities.stream().noneMatch(e -> e.toLowerCase().contains("complexity")), entities.toString());
	}

	@Test
	void extractsMarkdownBoldBulletLabelsFromStoredChat() {
		String answer = """
				Based on the provided notes, the most common and popular non-primitive data structures used in Data Structures and Algorithms (DSA) are:

				*   **Arrays**: Basic structures for storing collections of elements.
				*   **Linked Lists**: Used for organizing elements, such as in queue implementations [1].
				*   **Stacks**: Store elements in a Last-In-First-Out (LIFO) order.
				*   **Queues**: Store elements in a First-In-First-Out (FIFO) order.
				*   **Trees**: Complex structures built from primitive types [3].
				*   **Graphs**: Used for path algorithms and traversal [4].
				*   **Hash Tables**: Implement associative arrays to store key-value pairs.

				These structures are chosen based on the type and amount of data.
				""";
		List<String> entities = PriorTurnEntityExtractor.extractFromText(answer);
		assertEquals(
				List.of("Arrays", "Linked Lists", "Stacks", "Queues", "Trees", "Graphs", "Hash Tables"),
				entities
		);
	}
}
