package com.secondbrain.chat.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.secondbrain.chat.entity.ChatMessage;
import com.secondbrain.chat.entity.MessageRole;

/**
 * Pulls concrete list items / entities from the last assistant turn so follow-ups
 * like "complexity for these" can search and answer against the named items,
 * not only the vague pronoun.
 */
public final class PriorTurnEntityExtractor {

	private static final int MAX_ENTITIES = 12;
	private static final int MAX_ENTITY_LEN = 48;

	/** Bullet / numbered / plain short line list items. */
	private static final Pattern LIST_LINE = Pattern.compile(
			"^\\s*(?:[-*•]+|\\d+[.)])\\s+(.+?)\\s*$"
	);

	/** "are: A, B, and C" / "structures: A, B, C" */
	private static final Pattern COLON_LIST = Pattern.compile(
			"(?i)(?:are|include|including|namely|following|structures?|types?)\\s*[:—-]\\s*(.+)$"
	);

	/**
	 * "Stacks: Store elements in LIFO order…" — common model formatting.
	 * Label must be a short name, not a full sentence.
	 */
	private static final Pattern LABELED_ITEM = Pattern.compile(
			"^\\s*([A-Za-z][A-Za-z0-9 +/\\-]{0,40}):\\s+\\S.+"
	);

	private static final Pattern COMPLEXITY_HEADING = Pattern.compile(
			"(?i)\\b(?:time|space)\\s+complexity\\s*:"
	);

	private PriorTurnEntityExtractor() {
	}

	/**
	 * Walks assistant turns from newest to oldest. Skips "Time Complexity:"
	 * breakdowns so a second follow-up still refers to the original list
	 * (Arrays, Stacks, …), not the last answer's headings.
	 */
	public static List<String> extractFromPrior(List<ChatMessage> priorMessages) {
		if (priorMessages == null || priorMessages.isEmpty()) {
			return List.of();
		}
		for (int i = priorMessages.size() - 1; i >= 0; i--) {
			ChatMessage m = priorMessages.get(i);
			if (m.getRole() != MessageRole.ASSISTANT || m.getContent() == null || m.getContent().isBlank()) {
				continue;
			}
			if (looksLikeAttributeBreakdown(m.getContent())) {
				continue;
			}
			List<String> entities = extractFromText(m.getContent());
			if (entities.size() >= 2) {
				return entities;
			}
		}
		return List.of();
	}

	static List<String> extractFromText(String text) {
		Set<String> out = new LinkedHashSet<>();

		// Prefer explicit list lines
		for (String line : text.split("\\R")) {
			line = stripLeadingMarker(stripMarkdown(line));
			String candidate = null;
			Matcher labeled = LABELED_ITEM.matcher(line);
			if (labeled.matches()) {
				String label = labeled.group(1).strip();
				if (countWords(label) <= 5 && looksLikeEntityLine(label)) {
					candidate = label;
				}
			}
			Matcher m = LIST_LINE.matcher(line);
			if (candidate == null && m.matches()) {
				candidate = m.group(1);
			}
			if (candidate == null) {
				String trimmed = line.strip();
				// Drop trailing list labels like "Common structures:"
				if (trimmed.endsWith(":")) {
					continue;
				}
				// Short standalone lines that look like list entries (e.g. "Hash Table")
				if (trimmed.length() >= 2 && trimmed.length() <= MAX_ENTITY_LEN
						&& !trimmed.endsWith(".")
						&& !trimmed.endsWith("?")
						&& !trimmed.contains(":")
						&& !trimmed.contains("  ")
						&& countWords(trimmed) <= 5
						&& looksLikeEntityLine(trimmed)) {
					candidate = trimmed;
				}
			}
			addEntity(out, candidate);
			if (out.size() >= MAX_ENTITIES) {
				return List.copyOf(out);
			}
		}

		// Fallback: colon-separated prose lists
		if (out.size() < 2) {
			for (String line : text.split("\\R")) {
				Matcher m = COLON_LIST.matcher(line.strip());
				if (m.find()) {
					splitCommaList(m.group(1), out);
				}
				if (out.size() >= MAX_ENTITIES) {
					break;
				}
			}
		}

		// Last resort: first sentence with several comma-separated proper-ish tokens
		if (out.size() < 2) {
			String flat = text.replaceAll("\\s+", " ").strip();
			int period = flat.indexOf('.');
			String head = period > 0 ? flat.substring(0, period) : flat;
			if (head.contains(",")) {
				splitCommaList(head, out);
			}
		}

		return List.copyOf(out);
	}

	/**
	 * Ensures named entities appear in the retrieval query when the user used deixis.
	 */
	public static String mergeEntitiesIntoQuery(String query, List<String> entities) {
		if (query == null) {
			query = "";
		}
		if (entities == null || entities.isEmpty()) {
			return query.strip();
		}
		String lower = query.toLowerCase(Locale.ROOT);
		StringBuilder sb = new StringBuilder(query.strip());
		for (String e : entities) {
			if (e == null || e.isBlank()) {
				continue;
			}
			if (!lower.contains(e.toLowerCase(Locale.ROOT))) {
				if (sb.length() > 0) {
					sb.append(' ');
				}
				sb.append(e);
				lower = sb.toString().toLowerCase(Locale.ROOT);
			}
		}
		return sb.toString().strip();
	}

	/**
	 * Human-readable resolved question for the answer LLM.
	 */
	public static String buildResolvedQuestion(String original, List<String> entities) {
		if (original == null || original.isBlank()) {
			return original;
		}
		if (entities == null || entities.isEmpty()) {
			return original.strip();
		}
		String joined = String.join(", ", entities);
		String o = original.strip();
		String lower = o.toLowerCase(Locale.ROOT);
		if (lower.contains("complex")) {
			return "What is the time and space complexity of each of: " + joined + "?";
		}
		if (lower.matches("(?s).*(pros|cons|advantage|disadvantage).*")) {
			return "What are the pros and cons of each of: " + joined + "?";
		}
		if (lower.matches("(?s).*(example|use case).*")) {
			return "What are examples or use cases for each of: " + joined + "?";
		}
		// Generic: keep user intent words + explicit list
		return o + " — referring to: " + joined;
	}

	private static void splitCommaList(String list, Set<String> out) {
		String cleaned = list
				.replaceAll("(?i)\\band\\b", ",")
				.replace(';', ',');
		for (String part : cleaned.split(",")) {
			addEntity(out, part);
			if (out.size() >= MAX_ENTITIES) {
				return;
			}
		}
	}

	private static void addEntity(Set<String> out, String raw) {
		if (raw == null) {
			return;
		}
		String e = stripMarkdown(raw)
				.replaceAll("^[\\[\\(]+", "")
				.replaceAll("[\\]\\).,;:*]+$", "")
				.strip();
		// Drop citation noise like [1]
		e = e.replaceAll("\\[\\d+]", "").strip();
		if (e.length() < 2 || e.length() > MAX_ENTITY_LEN) {
			return;
		}
		if (isNoiseEntity(e)) {
			return;
		}
		out.add(e);
	}

	private static boolean looksLikeEntityLine(String trimmed) {
		String lower = trimmed.toLowerCase(Locale.ROOT);
		if (isNoiseEntity(trimmed)) {
			return false;
		}
		// Reject full sentences
		if (lower.startsWith("based on") || lower.startsWith("these ") || lower.startsWith("the ")) {
			return false;
		}
		if (lower.startsWith("sources:") || lower.startsWith("according")) {
			return false;
		}
		// Prefer Title Case / known DS-ish tokens / short noun phrases
		return Character.isLetter(trimmed.charAt(0));
	}

	private static boolean isNoiseEntity(String e) {
		String lower = stripMarkdown(e).toLowerCase(Locale.ROOT).strip();
		if (lower.isEmpty() || lower.equals("*") || lower.equals("**")) {
			return true;
		}
		// true = drop this token
		return switch (lower) {
			case "yes", "no", "ok", "okay", "sure", "note", "notes", "source", "sources",
					"example", "examples", "see", "also", "following", "above", "below",
					"or", "and", "the", "a", "an",
					"time", "space", "complexity",
					"time complexity", "space complexity",
					"advantages", "disadvantages", "advantage", "disadvantage",
					"pros", "cons", "analysis", "output", "applications",
					"operations", "definition", "introduction" -> true;
			default -> lower.equals("data structures") || lower.equals("data structure")
					|| lower.startsWith("http")
					|| lower.startsWith("time complexity")
					|| lower.startsWith("space complexity")
					|| lower.contains("provided notes")
					|| lower.contains("chunk ")
					|| lower.contains("complexity");
		};
	}

	static boolean looksLikeAttributeBreakdown(String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		Matcher m = COMPLEXITY_HEADING.matcher(text);
		int n = 0;
		while (m.find()) {
			n++;
			if (n >= 2) {
				return true;
			}
		}
		return false;
	}

	static String stripMarkdown(String raw) {
		if (raw == null) {
			return "";
		}
		return raw.replace("**", "").replace("__", "").replace("`", "").strip();
	}

	/** "*   Arrays: …" / "1. Stacks: …" → "Arrays: …" so labeled-name parsing can run. */
	static String stripLeadingMarker(String line) {
		if (line == null || line.isBlank()) {
			return "";
		}
		return line.replaceFirst("^\\s*(?:[-*•]+|\\d+[.)])\\s+", "").strip();
	}

	private static int countWords(String s) {
		String[] p = s.trim().split("\\s+");
		int n = 0;
		for (String x : p) {
			if (!x.isBlank()) {
				n++;
			}
		}
		return n;
	}

	/** Visible for tests — package helper list. */
	public static List<String> asMutableList(List<String> entities) {
		return new ArrayList<>(entities);
	}
}
