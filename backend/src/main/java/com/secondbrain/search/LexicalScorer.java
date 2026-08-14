package com.secondbrain.search;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight lexical ranking for the test vector store (and unit tests).
 * Production uses Postgres {@code tsvector} / {@code ts_rank_cd}.
 */
public final class LexicalScorer {

	private static final Pattern WORD = Pattern.compile("[a-z0-9]+");
	private static final Pattern BIG_O = Pattern.compile("o\\s*\\([^)]{1,24}\\)");

	private static final Set<String> STOP = Set.of(
			"the", "a", "an", "of", "and", "or", "to", "in", "on", "for",
			"is", "are", "was", "be", "as", "at", "by", "it", "this", "that"
	);

	private LexicalScorer() {
	}

	/**
	 * Higher is better. {@code 0} means no usable overlap (omit from keyword results).
	 */
	public static double score(String query, String content) {
		if (query == null || content == null) {
			return 0;
		}
		String qNorm = normalize(query);
		String cNorm = normalize(content);
		if (qNorm.isEmpty() || cNorm.isEmpty()) {
			return 0;
		}

		List<String> qTokens = tokens(qNorm);
		if (qTokens.isEmpty()) {
			return 0;
		}
		List<String> cTokens = tokens(cNorm);
		if (cTokens.isEmpty()) {
			return 0;
		}

		int matched = 0;
		double tf = 0;
		for (String t : qTokens) {
			int freq = 0;
			for (String c : cTokens) {
				if (c.equals(t)) {
					freq++;
				}
			}
			if (freq > 0) {
				matched++;
				tf += 1.0 + Math.log(freq);
			}
		}
		if (matched == 0) {
			return 0;
		}
		double coverage = (double) matched / qTokens.size();
		double phrase = cNorm.contains(qNorm) ? 1.5 : 0;
		boolean wantsComplexity = qNorm.contains("complex") || qNorm.contains("o(");
		double bigO = wantsComplexity && BIG_O.matcher(cNorm).find() ? 1.5 : 0;
		return tf * coverage + phrase + bigO;
	}

	static String normalize(String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
	}

	static List<String> tokens(String normalized) {
		LinkedHashSet<String> out = new LinkedHashSet<>();
		Matcher bigO = BIG_O.matcher(normalized);
		while (bigO.find()) {
			out.add(bigO.group().replaceAll("\\s+", ""));
		}
		Matcher words = WORD.matcher(normalized);
		while (words.find()) {
			String t = words.group();
			if (!STOP.contains(t)) {
				out.add(t);
			}
		}
		return new ArrayList<>(out);
	}
}
