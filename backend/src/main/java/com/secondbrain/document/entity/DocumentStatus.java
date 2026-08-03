package com.secondbrain.document.entity;

/**
 * Ingestion lifecycle for a document.
 * UPLOADED is set immediately after file storage; later pipeline stages advance status.
 */
public enum DocumentStatus {
	UPLOADED,
	PROCESSING,
	READY,
	FAILED
}
