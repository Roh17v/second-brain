package com.secondbrain.chat.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.secondbrain.chat.dto.CitationResponse;
import com.secondbrain.search.SearchHitResponse;

@Component
public class RagPromptBuilder {

	public static final String SYSTEM_PROMPT = """
			You are SecondBrain, a private personal knowledge assistant.
			Answer using ONLY the provided context sources when possible.
			If the context is insufficient, say you do not have enough information in the knowledge base.
			Do not invent facts.
			When you use a source, cite it like [1], [2] matching the source numbers.
			Be concise and clear.
			""".stripIndent();

	public record BuiltPrompt(String systemPrompt, String userPrompt, List<CitationResponse> citations) {
	}

	public BuiltPrompt build(String userQuestion, List<SearchHitResponse> hits, List<String> sourceFilenames) {
		if (hits == null || hits.isEmpty()) {
			String noContextUser = """
					Question: %s

					Context: (none)

					There are no retrieved sources. Tell the user you do not have enough information in their knowledge base.
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
				Use the context sources below to answer the question.
				Cite sources with [n] where n is the source number.

				Context:
				%s
				Question: %s
				""".formatted(context.toString().trim(), userQuestion).stripIndent();

		return new BuiltPrompt(SYSTEM_PROMPT, userPrompt, citations);
	}
}
