package com.secondbrain.chat.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.secondbrain.chat.dto.CitationResponse;
import com.secondbrain.search.SearchHitResponse;

@Component
public class RagPromptBuilder {

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
			   explicitly define…", "not mentioned as a general concept…", or "based on
			   examples only…". If notes only show examples (e.g. O(log n), O(n²)), still
			   state what the concept means in plain language, then illustrate with those
			   examples and cite them. Everyday CS terms may use standard meaning; do not
			   invent quotes, page numbers, or file-specific claims.
			4. If the context is relevant but incomplete for a non-definition ask, still answer
			   clearly: short overview first, then connect to what their notes cover, with citations.
			5. If the context is unrelated or empty, say you don't have enough in their
			   knowledge base for that specific ask — do not invent quotes from their docs.
			6. Do not invent page numbers, quotes, or file content that is not in context.
			7. Be concise and clear. Prefer structure (short paragraphs or bullets).
			8. Output ONLY the final answer the user should read. Never include chain-of-thought,
			   analysis drafts, self-checks, or XML/HTML think/reasoning tags.
			""".stripIndent();

	public record BuiltPrompt(String systemPrompt, String userPrompt, List<CitationResponse> citations) {
	}

	public BuiltPrompt build(String userQuestion, List<SearchHitResponse> hits, List<String> sourceFilenames) {
		if (hits == null || hits.isEmpty()) {
			String noContextUser = """
					Question: %s

					Context: (none)

					No retrieved sources from the user's knowledge base.
					Say you don't have enough information in their notes for this,
					and answer only if it is a pure clarification with no document claims.
					""".formatted(userQuestion).stripIndent();
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
			String snippet = hit.content();
			if (snippet.length() > 1200) {
				snippet = snippet.substring(0, 1200) + "...";
			}
			context.append("[").append(n).append("] source=").append(filename)
					.append(" chunk=").append(hit.chunkIndex())
					.append("\n")
					.append(hit.content())
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

				Answer the question as written.
				- For "what is / define / explain X": first sentence = definition. Then
				  examples/details from context with citations. Never lead with "not defined".
				- If context only has related examples, still define the concept, then map notes.

				Context:
				%s

				Question: %s
				""".formatted(context.toString().trim(), userQuestion).stripIndent();

		return new BuiltPrompt(SYSTEM_PROMPT, userPrompt, citations);
	}
}
