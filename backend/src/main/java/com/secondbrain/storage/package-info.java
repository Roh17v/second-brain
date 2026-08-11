/**
 * File storage abstraction for uploaded documents.
 * <p>
 * Implementations are selected with {@code app.storage.provider}:
 * <ul>
 *   <li>{@code local} — disk under {@code app.storage.base-path} (default)</li>
 *   <li>{@code b2} / {@code s3} — S3-compatible object storage (Backblaze B2, AWS, MinIO, …)</li>
 * </ul>
 * Callers use only {@link com.secondbrain.storage.FileStorageService}; the DB stores a relative key.
 */
package com.secondbrain.storage;
