package com.secondbrain.search;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a safe Postgres {@code tsquery} string. Tokens are OR-ed so a
 * follow-up like "time complexity space complexity Stack" can still hit a
 * chunk that only says "O(1) time complexity".
 */
public final class FullTextQuery {

	private static final Pattern TOKEN = Pattern.compile("[a-z0-9]+");

	private static final Set<String> STOP = Set.of(
			"the", "a", "an", "of", "and", "or", "to", "in", "on", "for",
			"is", "are", "was", "be", "as", "at", "by", "it", "this", "that"
	);

	private FullTextQuery() {
	}

	/**
	 * @return {@code token | token | ...} for {@code to_tsquery}, or empty if unusable
	 */
	public static String orTsQuery(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		LinkedHashSet<String> tokens = new LinkedHashSet<>();
		Matcher matcher = TOKEN.matcher(raw.toLowerCase(Locale.ROOT));
		while (matcher.find()) {
			String t = matcher.group();
			if (!STOP.contains(t)) {
				tokens.add(t);
			}
		}
		if (tokens.isEmpty()) {
			return "";
		}
		return String.join(" | ", tokens);
	}
}
