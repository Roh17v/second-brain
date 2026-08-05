package com.secondbrain.document.entity;

/**
 * Document ingestion lifecycle.
 * <ul>
 *   <li>{@link #UPLOADED} — file stored; pipeline queued</li>
 *   <li>{@link #PROCESSING} — extract / OCR / chunk</li>
 *   <li>{@link #EMBEDDING} — vector embeddings in progress</li>
 *   <li>{@link #READY} — searchable in chat</li>
 *   <li>{@link #FAILED} — see failureReason</li>
 * </ul>
 */
public enum DocumentStatus {
	UPLOADED,
	PROCESSING,
	EMBEDDING,
	READY,
	FAILED
}
