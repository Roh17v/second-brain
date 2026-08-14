/**
 * Chat / conversational RAG.
 * <p>
 * Retrieval pipeline (inspired by LangChain {@code create_history_aware_retriever}
 * + multi-query retrieval, implemented without LangChain):
 * <ol>
 *   <li><b>Gate</b> — {@link com.secondbrain.chat.service.ConversationAnalyzer}:
 *       cheap heuristics (pronouns, short follow-ups) decide if rewrite is needed.</li>
 *   <li><b>Standalone rewrite</b> — {@link com.secondbrain.chat.service.ContextualQueryService}:
 *       LLM turns "complexity for these" into a self-contained search string using history.</li>
 *   <li><b>Entity expand</b> — {@link com.secondbrain.chat.service.PriorTurnEntityExtractor}:
 *       pull Array/Stack/… from the prior assistant list when rewrite is weak.</li>
 *   <li><b>Multi-query</b> — one query per entity (+ combined), so vector search does not
 *       collapse a list follow-up into a single ambiguous embedding.</li>
 *   <li><b>Hybrid retrieve</b> — each search string hits dense ANN <em>and</em>
 *       keyword/FTS; lists are fused with
 *       {@link com.secondbrain.search.ReciprocalRankFusion} (multi-query uses
 *       the same fuse).</li>
 *   <li><b>Answer</b> — history + resolved question + fused chunks → LLM
 *       ({@link com.secondbrain.chat.service.RagPromptBuilder}).</li>
 * </ol>
 * History is always sent to the answer LLM; the hard problem is step 2–5 so the
 * <em>vector store</em> sees the right queries, not only the bare follow-up text.
 */
package com.secondbrain.chat.service;
