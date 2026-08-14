package com.secondbrain.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Retrieval settings (hybrid dense + keyword). Independent of embedding/LLM providers.
 */
@ConfigurationProperties(prefix = "app.search")
public class SearchProperties {

	/**
	 * When true, each query runs vector search and keyword/FTS, then RRF-fuses the lists.
	 */
	private boolean hybridEnabled = true;

	/**
	 * Per-channel candidate pool before fusion. Chat still receives the requested top-K.
	 */
	private int candidateK = 20;

	private int maxCandidateK = 50;

	/**
	 * Second-stage rerank of the candidate pool before the prompt.
	 */
	private boolean rerankEnabled = true;

	/**
	 * {@code lexical} (default) or {@code identity} (first-stage order only).
	 */
	private String rerankProvider = "lexical";

	/**
	 * Hits kept after rerank when the caller asked for fewer (chat default is 5).
	 */
	private int rerankTopK = 8;

	public boolean isHybridEnabled() {
		return hybridEnabled;
	}

	public void setHybridEnabled(boolean hybridEnabled) {
		this.hybridEnabled = hybridEnabled;
	}

	public int getCandidateK() {
		return candidateK;
	}

	public void setCandidateK(int candidateK) {
		this.candidateK = candidateK;
	}

	public int getMaxCandidateK() {
		return maxCandidateK;
	}

	public void setMaxCandidateK(int maxCandidateK) {
		this.maxCandidateK = maxCandidateK;
	}

	public boolean isRerankEnabled() {
		return rerankEnabled;
	}

	public void setRerankEnabled(boolean rerankEnabled) {
		this.rerankEnabled = rerankEnabled;
	}

	public String getRerankProvider() {
		return rerankProvider;
	}

	public void setRerankProvider(String rerankProvider) {
		this.rerankProvider = rerankProvider;
	}

	public int getRerankTopK() {
		return rerankTopK;
	}

	public void setRerankTopK(int rerankTopK) {
		this.rerankTopK = rerankTopK;
	}

	/**
	 * Widen first-stage retrieval so fusion / rerank has enough candidates.
	 */
	public int candidateLimit(int requestedTopK) {
		int k = requestedTopK <= 0 ? 5 : requestedTopK;
		int configured = candidateK <= 0 ? 20 : candidateK;
		int max = maxCandidateK <= 0 ? 50 : maxCandidateK;
		return Math.min(Math.max(k, configured), max);
	}

	public int finalTopK(int requestedTopK, int queryCount) {
		int k = requestedTopK <= 0 ? 5 : Math.min(requestedTopK, 50);
		if (rerankEnabled) {
			int floor = rerankTopK <= 0 ? 8 : rerankTopK;
			k = Math.max(k, floor);
		}
		if (queryCount > 1) {
			k = Math.max(k, Math.min(10, queryCount));
		}
		return Math.min(k, 50);
	}
}
