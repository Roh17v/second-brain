package com.secondbrain.chat.service;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Cheap, stateless conversation analysis for retrieval decisions.
 * <p>
 * Today: follow-up / rewrite gating. Evolves toward topic-shift, intent, multi-query, etc.
 * No Spring bean — pure heuristics, safe to call on the request thread.
 */
public final class ConversationAnalyzer {

	private static final Pattern PRONOUN_OR_DEIXIS = Pattern.compile(
			"(?i)\\b("
					+ "it|its|it's|"
					+ "this|that|these|those|"
					+ "them|they|their|theirs|"
					+ "he|she|his|her|hers|"
					+ "one|ones|"
					+ "here|there|"
					+ "same|above|former|latter|such|both|either|neither"
					+ ")\\b"
	);

	private static final Pattern CONTINUATION = Pattern.compile(
			"(?i)^\\s*("
					+ "what about|how about|"
					+ "and (for|about|the)\\b|"
					+ "also\\b|same for|"
					+ "compare (it|them|that|this)\\b|"
					+ "more details?\\b|"
					+ "elaborate\\b|"
					+ "explain (it|this|that|these|more)\\b|"
					+ "tell me more\\b|"
					+ "can you (expand|explain more|elaborate)\\b|"
					+ "what else\\b|anything else\\b|"
					+ "examples?\\b|use cases?\\b|"
					+ "pros and cons\\b|advantages?\\b|disadvantages?\\b|"
					+ "why so\\b|how so\\b|why is that\\b|"
					+ "go on\\b|continue\\b"
					+ ")"
	);

	/** Bare "why?" / "how?" / "what?" only — not "how does Redis work". */
	private static final Pattern BARE_WH_QUESTION = Pattern.compile(
			"(?i)^\\s*(why|how|what)\\s*\\??\\s*$"
	);

	private static final Pattern TECH_TOKEN = Pattern.compile(
			".*[A-Z].*[a-z].*" // CamelCase-ish
					+ "|_|-|\\d" // snake, kebab, versions
	);

	private static final int SHORT_FOLLOWUP_WORDS = 8;

	private ConversationAnalyzer() {
	}

	/**
	 * Decide whether the latest user message needs history-aware rewrite for retrieval.
	 *
	 * @param currentUserMessage latest user text (trimmed preferred)
	 * @param hasPriorUserTurns  true if conversation already has earlier user messages
	 */
	public static RewriteDecision analyze(String currentUserMessage, boolean hasPriorUserTurns) {
		if (!hasPriorUserTurns) {
			return RewriteDecision.no(0.99, "first_turn");
		}
		if (currentUserMessage == null || currentUserMessage.isBlank()) {
			return RewriteDecision.no(0.95, "empty");
		}

		String q = currentUserMessage.trim();

		if (PRONOUN_OR_DEIXIS.matcher(q).find()) {
			return RewriteDecision.yes(0.94, "pronoun_or_deixis");
		}
		if (CONTINUATION.matcher(q).find()) {
			return RewriteDecision.yes(0.92, "continuation_phrase");
		}
		if (BARE_WH_QUESTION.matcher(q).matches()) {
			return RewriteDecision.yes(0.96, "bare_wh_question");
		}

		int words = countWords(q);
		if (words > 0 && words <= SHORT_FOLLOWUP_WORDS && !looksStandalone(q)) {
			return RewriteDecision.yes(0.78, "short_ambiguous");
		}

		if (looksStandalone(q)) {
			double conf = hasStrongEntity(q) ? 0.90 : 0.82;
			return RewriteDecision.no(conf, "standalone");
		}

		// Longer but no clear entity — mild uncertainty; still skip LLM rewrite for cost
		// (future: UNSURE → cheap classifier). Prefer original for now.
		return RewriteDecision.no(0.70, "default_standalone");
	}

	/** Backward-compatible boolean API. */
	public static boolean needsRewrite(String currentUserMessage, boolean hasPriorUserTurns) {
		return analyze(currentUserMessage, hasPriorUserTurns).needsRewrite();
	}

	static boolean looksStandalone(String q) {
		String lower = q.toLowerCase(Locale.ROOT);
		if (lower.matches("(?i)^(why|how|what|when|where|who)\\??$")) {
			return false;
		}
		String[] tokens = q.split("[\\s,;:!?]+");
		int contentTokens = 0;
		for (String t : tokens) {
			if (t.length() < 2) {
				continue;
			}
			String tl = t.toLowerCase(Locale.ROOT);
			if (isStopish(tl)) {
				continue;
			}
			contentTokens++;
		}
		return contentTokens >= 1 && !PRONOUN_OR_DEIXIS.matcher(q).find();
	}

	static boolean hasStrongEntity(String q) {
		String[] tokens = q.split("[\\s,;:!?]+");
		for (String t : tokens) {
			if (t.length() < 2) {
				continue;
			}
			// ALL_CAPS acronyms (JWT, Redis-ish short caps), CamelCase, snake/kebab
			if (t.length() >= 2 && t.equals(t.toUpperCase(Locale.ROOT)) && t.chars().anyMatch(Character::isLetter)) {
				return true;
			}
			if (TECH_TOKEN.matcher(t).find()) {
				return true;
			}
			// Capitalized non-sentence-start words (rough): multi-word with mid capital
			if (t.length() > 1 && Character.isUpperCase(t.charAt(0)) && t.chars().skip(1).anyMatch(Character::isLowerCase)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Stop-ish tokens for standalone detection only — not for embedding.
	 * Keep retrieval terms like time/space/complexity out of this list.
	 */
	private static boolean isStopish(String tl) {
		return switch (tl) {
			case "what", "whats", "which", "when", "where", "who", "whom", "why", "how",
					"give", "tell", "show", "list", "explain", "describe", "about",
					"the", "and", "for", "with", "from", "into",
					"please", "more", "some", "any", "also", "just", "only",
					"need", "want", "does", "did", "can", "could", "would", "should", "will",
					"are", "is", "was", "were", "been", "being", "have", "has", "had",
					"you", "your", "me", "my", "our",
					"of", "a", "an", "to", "in", "on", "at", "by", "or" -> true;
			default -> false;
		};
	}

	private static int countWords(String q) {
		String[] parts = q.trim().split("\\s+");
		int n = 0;
		for (String p : parts) {
			if (!p.isBlank()) {
				n++;
			}
		}
		return n;
	}
}
