package com.secondbrain.chat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.secondbrain.ai.llm.LlmClient;
import com.secondbrain.ai.llm.LlmMessage;
import com.secondbrain.ai.llm.ThinkingStreamFilter;
import com.secondbrain.chat.entity.ChatMessage;
import com.secondbrain.chat.entity.MessageRole;

/**
 * History-aware retrieval query preparation (conversational query rewriting).
 * <p>
 * Pipeline: gate → optional LLM standalone rewrite → entity merge from prior answer
 * → validated search string + resolved question for the answer LLM.
 * Does <strong>not</strong> change the user-facing message stored in the DB.
 */
@Service
public class ContextualQueryService {

	private static final Logger log = LoggerFactory.getLogger(ContextualQueryService.class);

	private static final int MAX_HISTORY_TURNS = 6;
	private static final int MAX_HISTORY_CHARS_PER_MSG = 500;
	private static final int MAX_CONCAT_CHARS = 700;
	private static final int MAX_REWRITE_CHARS = 500;

	private static final String REWRITE_SYSTEM = """
			You rewrite chat follow-ups into standalone search queries for a personal knowledge base.

			Rules:
			- Output ONLY the rewritten search query text. No quotes, labels, markdown, or explanation.
			- Resolve pronouns and references (it, this, these, that, they, them, same, above…)
			  using the history. When the user says "these/those/them", ENUMERATE the concrete
			  items from the previous assistant or user list (e.g. Array, Stack, Queue…).
			- Combine the user's attribute words from the latest message (complexity, pros/cons,
			  examples, definition, …) with those named items.
			- Example: history listed Array, Stack, Queue and user says "complexity for these"
			  → output like: "time and space complexity of Array Stack Queue"
			- Preserve the user's intent; do not answer the question.
			- Do not invent topics not implied by the history + latest message.
			- If the latest message is already self-contained, output it unchanged (cleaned).
			- Keep the query concise (one sentence or a short keyword-rich phrase).
			""".stripIndent();

	private final LlmClient llmClient;

	public ContextualQueryService(LlmClient llmClient) {
		this.llmClient = llmClient;
	}

	/**
	 * @param currentUserMessage latest user text
	 * @param priorMessages      conversation messages <em>before</em> the latest user turn
	 */
	public ContextualQueryResult prepare(String currentUserMessage, List<ChatMessage> priorMessages) {
		String original = currentUserMessage == null ? "" : currentUserMessage.trim();
		if (original.isEmpty()) {
			return new ContextualQueryResult(
					original,
					original,
					original,
					false,
					ContextualQueryResult.METHOD_ORIGINAL
			);
		}

		boolean hasPriorUser = priorMessages != null && priorMessages.stream()
				.anyMatch(m -> m.getRole() == MessageRole.USER);

		List<String> entities = PriorTurnEntityExtractor.extractFromPrior(priorMessages);
		boolean deixis = hasDeixis(original);
		boolean listFollowUp = (deixis || isAttributeFollowUp(original)) && entities.size() >= 2;

		RewriteDecision decision = ConversationAnalyzer.analyze(original, hasPriorUser);
		if (!decision.needsRewrite()) {
			// Still expand if we have list entities and a short attribute follow-up without rewrite flag
			if (hasPriorUser && !entities.isEmpty() && isAttributeFollowUp(original)) {
				return entityExpandedResult(original, entities, "attribute_followup");
			}
			log.debug(
					"RAG query gate: original (reason={}, confidence={})",
					decision.reason(),
					decision.confidence()
			);
			return new ContextualQueryResult(
					original,
					original,
					List.of(original),
					original,
					false,
					ContextualQueryResult.METHOD_ORIGINAL
			);
		}

		log.debug(
				"RAG query gate: rewrite (reason={}, confidence={})",
				decision.reason(),
				decision.confidence()
		);

		String searchBase = null;
		String method = ContextualQueryResult.METHOD_REWRITE_FALLBACK_CONCAT;

		try {
			String rewritten = rewriteWithLlm(original, priorMessages);
			String cleaned = sanitizeRewrite(rewritten, original);
			if (cleaned != null) {
				searchBase = cleaned;
				method = ContextualQueryResult.METHOD_LLM_REWRITE;
				log.info(
						"RAG query rewrite OK reason={} original='{}' searchQuery='{}'",
						decision.reason(),
						truncateForLog(original, 80),
						truncateForLog(cleaned, 120)
				);
			}
			else {
				log.warn("RAG query rewrite invalid; falling back to history concat");
			}
		}
		catch (Exception ex) {
			log.warn("RAG query rewrite failed ({}): falling back to history concat", ex.getMessage());
		}

		if (searchBase == null) {
			searchBase = historyConcat(original, priorMessages);
			method = ContextualQueryResult.METHOD_REWRITE_FALLBACK_CONCAT;
		}

		// Deterministic entity merge: fix weak rewrites that drop the list under "these"
		if ((deixis || isAttributeFollowUp(original)) && !entities.isEmpty()) {
			String merged = PriorTurnEntityExtractor.mergeEntitiesIntoQuery(searchBase, entities);
			if (!merged.equalsIgnoreCase(searchBase.strip())) {
				log.info(
						"RAG query entity-merge entities={} before='{}' after='{}'",
						entities,
						truncateForLog(searchBase, 80),
						truncateForLog(merged, 120)
				);
				searchBase = merged;
				if (!method.equals(ContextualQueryResult.METHOD_LLM_REWRITE)) {
					method = ContextualQueryResult.METHOD_ENTITY_EXPAND;
				}
			}
			// Prefer keyword-focused query over long history concat when we have entities
			if (method.equals(ContextualQueryResult.METHOD_REWRITE_FALLBACK_CONCAT)
					|| searchBase.length() > 280) {
				searchBase = buildEntitySearchQuery(original, entities);
				method = ContextualQueryResult.METHOD_ENTITY_EXPAND;
			}
		}

		String resolved = (deixis || isAttributeFollowUp(original)) && !entities.isEmpty()
				? PriorTurnEntityExtractor.buildResolvedQuestion(original, entities)
				: original;

		List<String> multi = listFollowUp
				? buildMultiQueries(original, searchBase, entities)
				: List.of(searchBase);
		if (multi.size() > 1) {
			method = ContextualQueryResult.METHOD_MULTI_QUERY;
			log.info(
					"RAG multi-query plan count={} primary='{}' sample='{}'",
					multi.size(),
					truncateForLog(searchBase, 80),
					truncateForLog(multi.size() > 1 ? multi.get(1) : "", 60)
			);
		}

		return new ContextualQueryResult(
				original,
				searchBase,
				multi,
				resolved,
				true,
				method
		);
	}

	private ContextualQueryResult entityExpandedResult(
			String original,
			List<String> entities,
			String reason
	) {
		String search = buildEntitySearchQuery(original, entities);
		String resolved = PriorTurnEntityExtractor.buildResolvedQuestion(original, entities);
		List<String> multi = entities.size() >= 2
				? buildMultiQueries(original, search, entities)
				: List.of(search);
		String method = multi.size() > 1
				? ContextualQueryResult.METHOD_MULTI_QUERY
				: ContextualQueryResult.METHOD_ENTITY_EXPAND;
		log.info(
				"RAG query entity-expand reason={} original='{}' search='{}' queries={} resolved='{}'",
				reason,
				truncateForLog(original, 80),
				truncateForLog(search, 120),
				multi.size(),
				truncateForLog(resolved, 120)
		);
		return new ContextualQueryResult(
				original,
				search,
				multi,
				resolved,
				true,
				method
		);
	}

	/**
	 * LangChain MultiQuery-style expansion: one focused query per prior entity
	 * plus the combined primary query. Caps entity fan-out for cost.
	 */
	static List<String> buildMultiQueries(String original, String primarySearch, List<String> entities) {
		List<String> out = new ArrayList<>();
		if (primarySearch != null && !primarySearch.isBlank()) {
			out.add(primarySearch.strip());
		}
		if (entities == null || entities.isEmpty()) {
			return out.isEmpty() ? List.of(original == null ? "" : original) : out;
		}
		String prefix = attributePrefix(original);
		int maxEntityQueries = 8;
		int added = 0;
		for (String e : entities) {
			if (e == null || e.isBlank()) {
				continue;
			}
			String q = (prefix + " " + e).replaceAll("\\s+", " ").strip();
			if (q.isBlank()) {
				continue;
			}
			boolean dup = out.stream().anyMatch(x -> x.equalsIgnoreCase(q));
			if (!dup) {
				out.add(q);
				added++;
			}
			if (added >= maxEntityQueries) {
				break;
			}
		}
		return out;
	}

	static String attributePrefix(String original) {
		String o = original == null ? "" : original.strip();
		String lower = o.toLowerCase(Locale.ROOT);
		if (lower.contains("complex")) {
			return "time complexity";
		}
		if (lower.matches("(?s).*(pros|cons|advantage|disadvantage).*")) {
			return "pros cons advantages disadvantages";
		}
		if (lower.matches("(?s).*(example|use case).*")) {
			return "examples use cases";
		}
		StringBuilder sb = new StringBuilder();
		for (String w : o.split("\\s+")) {
			String tl = w.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_+-]", "");
			if (tl.length() < 2 || isDeixisToken(tl)) {
				continue;
			}
			sb.append(w).append(' ');
		}
		String p = sb.toString().strip();
		return p.isBlank() ? "details" : p;
	}

	static String buildEntitySearchQuery(String original, List<String> entities) {
		String prefix = attributePrefix(original);
		return (prefix + " " + String.join(" ", entities)).replaceAll("\\s+", " ").strip();
	}

	static boolean hasDeixis(String q) {
		if (q == null || q.isBlank()) {
			return false;
		}
		return ConversationAnalyzer.analyze(q, true).reason().equals("pronoun_or_deixis")
				|| q.toLowerCase(Locale.ROOT).matches("(?s).*\\b(these|those|them|they|it|this|that)\\b.*");
	}

	/**
	 * Short follow-ups that attach an attribute to a prior list without strong pronouns
	 * (edge cases). "complexity?" alone is already handled as short_ambiguous / bare.
	 */
	static boolean isAttributeFollowUp(String q) {
		if (q == null) {
			return false;
		}
		String lower = q.toLowerCase(Locale.ROOT).strip();
		int words = lower.split("\\s+").length;
		if (words > 10) {
			return false;
		}
		return lower.contains("complex")
				|| lower.contains("pros")
				|| lower.contains("cons")
				|| lower.contains("advantage")
				|| lower.contains("disadvantage")
				|| lower.contains("example")
				|| lower.contains("use case")
				|| lower.contains("trade-off")
				|| lower.contains("tradeoff");
	}

	private static boolean isDeixisToken(String tl) {
		return switch (tl) {
			case "it", "its", "this", "that", "these", "those", "them", "they",
					"their", "theirs", "same", "above", "for", "the", "a", "an",
					"of", "to", "and", "or", "give", "me", "please" -> true;
			default -> false;
		};
	}

	private String rewriteWithLlm(String original, List<ChatMessage> priorMessages) {
		String historyBlock = formatHistory(priorMessages);
		String userPayload = """
				Conversation history (oldest first):
				%s

				Latest user message:
				%s

				Standalone search query:
				""".formatted(
				historyBlock.isBlank() ? "(none)" : historyBlock,
				original
		).stripIndent();

		String raw = llmClient.chat(REWRITE_SYSTEM, List.of(LlmMessage.user(userPayload)));
		return ThinkingStreamFilter.stripComplete(raw == null ? "" : raw);
	}

	/**
	 * @return cleaned query or null if unusable
	 */
	static String sanitizeRewrite(String rewritten, String original) {
		if (rewritten == null || rewritten.isBlank()) {
			return null;
		}
		String s = rewritten.strip();
		if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
			s = s.substring(1, s.length() - 1).strip();
		}
		int nl = s.indexOf('\n');
		if (nl >= 0) {
			s = s.substring(0, nl).strip();
		}
		String lower = s.toLowerCase(Locale.ROOT);
		if (lower.startsWith("echo answer")
				|| lower.contains("based on retrieved context")
				|| lower.startsWith("here is")
				|| lower.startsWith("sure,")
				|| s.length() > MAX_REWRITE_CHARS) {
			return null;
		}
		if (s.isBlank()) {
			return null;
		}
		if (s.contains("Standalone search query:") || s.contains("Conversation history")) {
			return null;
		}
		return s;
	}

	static String historyConcat(String original, List<ChatMessage> priorMessages) {
		if (priorMessages == null || priorMessages.isEmpty()) {
			return original;
		}
		List<ChatMessage> window = tail(priorMessages, 4);
		StringBuilder sb = new StringBuilder();
		for (ChatMessage m : window) {
			if (m.getRole() != MessageRole.USER && m.getRole() != MessageRole.ASSISTANT) {
				continue;
			}
			String role = m.getRole() == MessageRole.USER ? "User" : "Assistant";
			String content = m.getContent() == null ? "" : m.getContent().replaceAll("\\s+", " ").strip();
			if (content.length() > MAX_HISTORY_CHARS_PER_MSG) {
				content = content.substring(0, MAX_HISTORY_CHARS_PER_MSG) + "…";
			}
			if (content.isBlank()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(role).append(": ").append(content);
		}
		if (sb.length() > 0) {
			sb.append(' ');
		}
		sb.append("User: ").append(original);
		String out = sb.toString().strip();
		if (out.length() > MAX_CONCAT_CHARS) {
			out = out.substring(out.length() - MAX_CONCAT_CHARS);
		}
		return out;
	}

	private static String formatHistory(List<ChatMessage> priorMessages) {
		if (priorMessages == null || priorMessages.isEmpty()) {
			return "";
		}
		List<ChatMessage> window = tail(priorMessages, MAX_HISTORY_TURNS);
		StringBuilder sb = new StringBuilder();
		for (ChatMessage m : window) {
			if (m.getRole() != MessageRole.USER && m.getRole() != MessageRole.ASSISTANT) {
				continue;
			}
			String role = m.getRole() == MessageRole.USER ? "User" : "Assistant";
			String content = m.getContent() == null ? "" : m.getContent().replaceAll("\\s+", " ").strip();
			if (content.length() > MAX_HISTORY_CHARS_PER_MSG) {
				content = content.substring(0, MAX_HISTORY_CHARS_PER_MSG) + "…";
			}
			if (content.isBlank()) {
				continue;
			}
			sb.append(role).append(": ").append(content).append('\n');
		}
		return sb.toString().strip();
	}

	private static List<ChatMessage> tail(List<ChatMessage> list, int n) {
		if (list.size() <= n) {
			return list;
		}
		return new ArrayList<>(list.subList(list.size() - n, list.size()));
	}

	private static String truncateForLog(String s, int max) {
		if (s == null) {
			return "";
		}
		String t = s.replaceAll("\\s+", " ").strip();
		return t.length() <= max ? t : t.substring(0, max) + "…";
	}
}
