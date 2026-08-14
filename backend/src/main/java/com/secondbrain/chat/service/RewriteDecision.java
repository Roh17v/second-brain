package com.secondbrain.chat.service;

/**
 * Outcome of cheap conversation analysis before retrieval.
 * {@link #needsRewrite()} drives whether to run the LLM rewriter.
 */
public record RewriteDecision(
		boolean needsRewrite,
		double confidence,
		String reason
) {
	public static RewriteDecision no(double confidence, String reason) {
		return new RewriteDecision(false, confidence, reason);
	}

	public static RewriteDecision yes(double confidence, String reason) {
		return new RewriteDecision(true, confidence, reason);
	}
}
