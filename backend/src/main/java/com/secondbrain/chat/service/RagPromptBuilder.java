package com.secondbrain.chat.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.secondbrain.chat.dto.CitationResponse;
import com.secondbrain.search.SearchHitResponse;

@Component
public class RagPromptBuilder {

	/** Soft cap per retrieved chunk in the answer prompt (chars). */
	static final int MAX_CONTEXT_CHUNK_CHARS = 1000;

	/**
	 * Grounded assistant: answer the user's actual question; use notes when helpful;
	 * never invent document-specific facts or answer a different question.
	 */
	public static final String SYSTEM_PROMPT = """
			You are SecondBrain, a private personal knowledge assistant for the user's notes.

			Core rules:
			1. Answer the USER'S ACTUAL QUESTION. Do not substitute a different topic
			   (e.g. if they ask "what is system design?", do NOT only ramble about
			   "scale to a million users" unless that is what they asked).
			2. Prefer facts from the provided context sources. Cite them as [1], [2], …
			   matching the source numbers. Cite only sources you actually used.
			3. "What is X?" / definition questions: ALWAYS lead with a clear, direct definition
			   in the first 1–2 sentences. Do NOT open with hedges like "the notes do not
			   explicitly define…". If notes only show examples (e.g. O(log n), O(n²)), still
			   state what the concept means in plain language, then illustrate with those
			   examples and cite them. Everyday CS terms may use standard meaning; do not
			   invent quotes, page numbers, or file-specific claims.
			4. When the latest message uses references such as "it", "they", "these", or "that",
			   resolve them from the conversation history before answering. If a "Resolved question"
			   line is provided, treat THAT as the question you must answer (e.g. cover each
			   listed data structure). Do not swap the topic to whatever random chunks mention
			   (e.g. only heaps) when the user asked about a list of structures.
			5. If the context is relevant but incomplete, still answer clearly: short overview
			   first, then connect to what their notes cover, with citations. For multi-item
			   questions (lists), go item-by-item: use notes where present; for items not in
			   the context, say the notes did not cover that item (do not invent complexities).
			6. If the context is unrelated or empty, say you don't have enough in their
			   knowledge base for that specific ask — do not invent quotes from their docs.
			7. Context may come from noisy OCR (scanned PDFs). Ignore OCR artifacts, malformed
			   tokens, and broken formatting; prefer coherent sentences.
			8. Do not invent page numbers, quotes, or file content that is not in context.
			9. Be concise and clear. Prefer structure (short paragraphs or bullets).
			   If you use a markdown table, put EACH row on its own line (header, separator,
			   then one row per line). Never squash a table into a single line.
			10. Output ONLY the final answer the user should read. Never include chain-of-thought,
			   analysis drafts, self-checks, or XML/HTML think/reasoning tags.
			""".stripIndent();

	public record BuiltPrompt(String systemPrompt, String userPrompt, List<CitationResponse> citations) {
	}

	public BuiltPrompt build(String userQuestion, List<SearchHitResponse> hits, List<String> sourceFilenames) {
		return build(userQuestion, userQuestion, hits, sourceFilenames);
	}

	/**
	 * @param userQuestion      raw latest user text (may contain "these")
	 * @param resolvedQuestion  expanded question for multi-turn follow-ups; if null/blank/same, only raw is shown
	 */
	public BuiltPrompt build(
			String userQuestion,
			String resolvedQuestion,
			List<SearchHitResponse> hits,
			List<String> sourceFilenames
	) {
		String questionBlock = formatQuestionBlock(userQuestion, resolvedQuestion);

		if (hits == null || hits.isEmpty()) {
			String noContextUser = """
					%s

					Context: (none)

					No retrieved sources from the user's knowledge base.
					Say you don't have enough information in their notes for this,
					and answer only if it is a pure clarification with no document claims.
					""".formatted(questionBlock).stripIndent();
			return new BuiltPrompt(SYSTEM_PROMPT, noContextUser, List.of());
		}

		StringBuilder context = new StringBuilder();
		List<CitationResponse> citations = new ArrayList<>();
		for (int i = 0; i < hits.size(); i++) {
			SearchHitResponse hit = hits.get(i);
			int n = i + 1;
			String filename = (sourceFilenames != null && i < sourceFilenames.size())
					? sourceFilenames.get(i)
					: "unknown";
			String full = hit.content() == null ? "" : hit.content();
			String forPrompt = truncate(full, MAX_CONTEXT_CHUNK_CHARS);
			String snippet = truncate(full, 1200);

			context.append("[").append(n).append("] source=").append(filename)
					.append(" chunk=").append(hit.chunkIndex());
			String section = sectionHeadingOf(full);
			if (section != null) {
				context.append(" section=").append(section);
			}
			context.append("\n")
					.append(forPrompt)
					.append("\n\n");
			citations.add(new CitationResponse(
					n,
					hit.chunkId(),
					hit.documentId(),
					filename,
					hit.chunkIndex(),
					hit.score(),
					snippet
			));
		}

		String userPrompt = """
				Retrieved context from the user's knowledge base (may be partial or noisy OCR).
				Use it to ground your answer when it helps. Cite with [n].
				If the resolved question lists multiple items, address each item; do not
				replace the whole answer with a single related topic that appears in context.

				Context:
				%s

				%s
				""".formatted(context.toString().trim(), questionBlock).stripIndent();

		return new BuiltPrompt(SYSTEM_PROMPT, userPrompt, citations);
	}

	static String formatQuestionBlock(String userQuestion, String resolvedQuestion) {
		String raw = userQuestion == null ? "" : userQuestion.strip();
		String resolved = resolvedQuestion == null ? "" : resolvedQuestion.strip();
		if (resolved.isBlank() || resolved.equalsIgnoreCase(raw)) {
			return "Question: " + raw;
		}
		return """
				User message: %s
				Resolved question (answer this fully): %s
				""".formatted(raw, resolved).stripIndent().strip();
	}

	static String sectionHeadingOf(String content) {
		if (content == null || !content.startsWith("Section: ")) {
			return null;
		}
		int nl = content.indexOf('\n');
		String heading = (nl < 0 ? content.substring("Section: ".length()) : content.substring("Section: ".length(), nl))
				.strip();
		return heading.isEmpty() ? null : heading;
	}

	static String truncate(String text, int maxChars) {
		if (text == null || text.length() <= maxChars) {
			return text == null ? "" : text;
		}
		return text.substring(0, maxChars) + "...";
	}
}
