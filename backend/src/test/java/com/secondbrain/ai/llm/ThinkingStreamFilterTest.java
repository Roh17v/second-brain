package com.secondbrain.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ThinkingStreamFilterTest {

	@Test
	void stripComplete_removesThinkBlock() {
		String open = "<" + "think" + ">";
		String close = "</" + "think" + ">";
		String raw = open + " secret reasoning " + close + "\n\nFinal answer about trees.";
		assertEquals("Final answer about trees.", ThinkingStreamFilter.stripComplete(raw));
	}

	@Test
	void stream_filtersSplitTags() {
		String open = "<" + "think" + ">";
		String close = "</" + "think" + ">";
		ThinkingStreamFilter f = new ThinkingStreamFilter();
		StringBuilder out = new StringBuilder();
		out.append(f.accept(open.substring(0, 3)));
		out.append(f.accept(open.substring(3)));
		out.append(f.accept(" hidden "));
		out.append(f.accept(close.substring(0, 4)));
		out.append(f.accept(close.substring(4)));
		out.append(f.accept("Visible answer"));
		out.append(f.finish());
		assertEquals("Visible answer", out.toString());
		assertFalse(out.toString().contains("hidden"));
	}

	@Test
	void stream_passesThroughCleanText() {
		ThinkingStreamFilter f = new ThinkingStreamFilter();
		assertEquals("Hello ", f.accept("Hello "));
		assertEquals("world", f.accept("world"));
		assertEquals("", f.finish());
	}
}
