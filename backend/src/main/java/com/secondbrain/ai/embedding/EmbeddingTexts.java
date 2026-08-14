package com.secondbrain.ai.embedding;

import java.util.Locale;

/**
 * Provider-specific input shaping. Nomic was trained with
 * {@code search_document:} / {@code search_query:} prefixes.
 */
public final class EmbeddingTexts {

	private EmbeddingTexts() {
	}

	public static String prepare(String model, String text, EmbeddingTask task) {
		if (text == null || text.isBlank()) {
			return text;
		}
		if (!isNomic(model)) {
			return text;
		}
		if (text.startsWith("search_document:") || text.startsWith("search_query:")) {
			return text;
		}
		String prefix = task == EmbeddingTask.QUERY ? "search_query: " : "search_document: ";
		return prefix + text;
	}

	static boolean isNomic(String model) {
		return model != null && model.toLowerCase(Locale.ROOT).contains("nomic");
	}
}
