package com.secondbrain.storage;

/**
 * Canonical ids for {@code app.storage.provider}.
 */
public final class StorageProviders {

	public static final String LOCAL = "local";
	/** Backblaze B2 via S3-compatible API. */
	public static final String B2 = "b2";
	/** Generic S3-compatible (AWS, MinIO, R2, …) — same client as B2. */
	public static final String S3 = "s3";

	private StorageProviders() {
	}
}
