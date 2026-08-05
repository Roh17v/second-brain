package com.secondbrain.ai.llm;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Strips model chain-of-thought so only the user-facing answer is shown/stored.
 * <p>
 * Qwen-family models often emit think blocks inside {@code content}. Tags may
 * split across stream chunks — this filter is stateful for streaming and has a
 * full-string helper for non-stream / finalize.
 */
public final class ThinkingStreamFilter {

	// Built at runtime so tooling cannot rewrite the literal tag names.
	private static final String[] OPEN_TAGS = {
			tag("think"),
			tag("redacted_reasoning"),
			tag("thinking"),
			tag("reasoning")
	};
	private static final String[] CLOSE_TAGS = {
			close("think"),
			close("redacted_reasoning"),
			close("thinking"),
			close("reasoning")
	};

	private static final Pattern COMPLETE_BLOCKS = Pattern.compile(
			"(?is)" + Pattern.quote(tag("think")) + ".*?" + Pattern.quote(close("think"))
					+ "|" + Pattern.quote(tag("redacted_reasoning")) + ".*?" + Pattern.quote(close("redacted_reasoning"))
					+ "|" + Pattern.quote(tag("thinking")) + ".*?" + Pattern.quote(close("thinking"))
					+ "|" + Pattern.quote(tag("reasoning")) + ".*?" + Pattern.quote(close("reasoning"))
	);
	private static final Pattern ORPHAN_OPEN = Pattern.compile(
			"(?is)" + Pattern.quote(tag("think")) + ".*"
					+ "|" + Pattern.quote(tag("redacted_reasoning")) + ".*"
					+ "|" + Pattern.quote(tag("thinking")) + ".*"
					+ "|" + Pattern.quote(tag("reasoning")) + ".*"
	);

	private boolean insideThink;
	private final StringBuilder hold = new StringBuilder();

	private static String tag(String name) {
		return "<" + name + ">";
	}

	private static String close(String name) {
		return "</" + name + ">";
	}

	/**
	 * Feed a stream delta; returns text safe to emit (may be empty while inside think).
	 */
	public String accept(String delta) {
		if (delta == null || delta.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		hold.append(delta);
		processHold(out);
		return out.toString();
	}

	/**
	 * Flush remaining buffered text at end of stream (drops unfinished think blocks).
	 */
	public String finish() {
		if (insideThink) {
			hold.setLength(0);
			return "";
		}
		String rest = hold.toString();
		hold.setLength(0);
		return rest;
	}

	/** Remove complete (and orphan-open) think blocks from a finished answer. */
	public static String stripComplete(String text) {
		if (text == null || text.isBlank()) {
			return text == null ? "" : text;
		}
		String s = COMPLETE_BLOCKS.matcher(text).replaceAll("");
		s = ORPHAN_OPEN.matcher(s).replaceAll("");
		return s.strip();
	}

	private void processHold(StringBuilder out) {
		while (!hold.isEmpty()) {
			if (insideThink) {
				int closeAt = indexOfAnyIgnoreCase(hold, CLOSE_TAGS);
				if (closeAt < 0) {
					int keep = maxTagLen(CLOSE_TAGS) - 1;
					if (hold.length() > keep) {
						hold.delete(0, hold.length() - keep);
					}
					return;
				}
				int closeLen = matchLenAt(hold, closeAt, CLOSE_TAGS);
				hold.delete(0, closeAt + closeLen);
				insideThink = false;
				continue;
			}

			int openAt = indexOfAnyIgnoreCase(hold, OPEN_TAGS);
			if (openAt < 0) {
				int maxPrefix = maxTagLen(OPEN_TAGS) - 1;
				int safeEnd = hold.length() - maxPrefix;
				if (safeEnd > 0) {
					out.append(hold, 0, safeEnd);
					hold.delete(0, safeEnd);
				}
				if (couldBeOpenPrefix(hold.toString())) {
					return;
				}
				out.append(hold);
				hold.setLength(0);
				return;
			}

			if (openAt > 0) {
				out.append(hold, 0, openAt);
			}
			int openLen = matchLenAt(hold, openAt, OPEN_TAGS);
			hold.delete(0, openAt + openLen);
			insideThink = true;
		}
	}

	private static int indexOfAnyIgnoreCase(StringBuilder buf, String[] tags) {
		String lower = buf.toString().toLowerCase(Locale.ROOT);
		int best = -1;
		for (String tag : tags) {
			int i = lower.indexOf(tag.toLowerCase(Locale.ROOT));
			if (i >= 0 && (best < 0 || i < best)) {
				best = i;
			}
		}
		return best;
	}

	private static int matchLenAt(StringBuilder buf, int at, String[] tags) {
		String slice = buf.substring(at).toLowerCase(Locale.ROOT);
		int best = 0;
		for (String tag : tags) {
			String t = tag.toLowerCase(Locale.ROOT);
			if (slice.startsWith(t) && t.length() > best) {
				best = t.length();
			}
		}
		return best;
	}

	private static int maxTagLen(String[] tags) {
		int m = 1;
		for (String t : tags) {
			m = Math.max(m, t.length());
		}
		return m;
	}

	private static boolean couldBeOpenPrefix(String s) {
		if (s.isEmpty()) {
			return false;
		}
		String lower = s.toLowerCase(Locale.ROOT);
		for (String tag : OPEN_TAGS) {
			if (tag.toLowerCase(Locale.ROOT).startsWith(lower)) {
				return true;
			}
		}
		return false;
	}
}