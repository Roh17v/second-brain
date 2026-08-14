package com.secondbrain.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConversationAnalyzerTest {

	@Test
	void firstTurnNeverRewrites() {
		RewriteDecision d = ConversationAnalyzer.analyze("Give complexity for these", false);
		assertFalse(d.needsRewrite());
		assertEquals("first_turn", d.reason());
	}

	@Test
	void pronounFollowUpsNeedRewrite() {
		assertTrue(ConversationAnalyzer.analyze("Give complexity for these", true).needsRewrite());
		assertTrue(ConversationAnalyzer.analyze("What about its persistence?", true).needsRewrite());
		assertTrue(ConversationAnalyzer.analyze("Which one is faster?", true).needsRewrite());
		assertTrue(ConversationAnalyzer.analyze("explain that simply", true).needsRewrite());
	}

	@Test
	void continuationPhrasesNeedRewrite() {
		assertTrue(ConversationAnalyzer.analyze("What about stacks?", true).needsRewrite());
		assertTrue(ConversationAnalyzer.analyze("tell me more", true).needsRewrite());
		assertTrue(ConversationAnalyzer.analyze("pros and cons?", true).needsRewrite());
		assertTrue(ConversationAnalyzer.analyze("why is that?", true).needsRewrite());
	}

	@Test
	void standaloneQuestionsSkipRewrite() {
		assertFalse(ConversationAnalyzer.analyze("What is Redis?", true).needsRewrite());
		assertFalse(ConversationAnalyzer.analyze("Explain PostgreSQL indexes in detail please", true).needsRewrite());
		assertFalse(ConversationAnalyzer.analyze("How does Spring dependency injection work?", true).needsRewrite());
		assertFalse(ConversationAnalyzer.analyze("Time complexity of HashMap", true).needsRewrite());
	}

	@Test
	void bareWhyNeedsRewriteWithHistory() {
		RewriteDecision d = ConversationAnalyzer.analyze("Why?", true);
		assertTrue(d.needsRewrite());
		assertEquals("bare_wh_question", d.reason());
	}
}
