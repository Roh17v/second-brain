package com.secondbrain.chat.service;

/**
 * @deprecated Use {@link ConversationAnalyzer} — kept as a thin alias for older references.
 */
@Deprecated
public final class FollowUpQueryGate {

	private FollowUpQueryGate() {
	}

	public static boolean needsRewrite(String currentUserMessage, boolean hasPriorUserTurns) {
		return ConversationAnalyzer.needsRewrite(currentUserMessage, hasPriorUserTurns);
	}
}
