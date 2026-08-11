package com.secondbrain.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import com.secondbrain.common.exception.StorageException;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Object storage via S3 API — Backblaze B2, AWS S3, MinIO, Cloudflare R2, etc.
 * <p>
 * Active when {@code app.storage.provider} is {@code b2} or {@code s3}.
 * DB stores the relative key (no bucket/prefix) so rows stay portable.
 */
@Service
@ConditionalOnExpression(
		"T(java.util.Arrays).asList('b2','s3').contains('${app.storage.provider:local}'.toLowerCase())"
)
public class S3CompatibleFileStorageService implements FileStorageService {

	private static final Logger log = LoggerFactory.getLogger(S3CompatibleFileStorageService.class);

	private final S3Client s3Client;
	private final String bucket;
	private final String keyPrefix;

	public S3CompatibleFileStorageService(S3Client s3Client, StorageProperties properties) {
		this.s3Client = s3Client;
		this.bucket = require(properties.getBucket(), "app.storage.bucket / STORAGE_BUCKET");
		this.keyPrefix = properties.normalizedKeyPrefix();
		log.info(
				"Object storage ready (provider={}, bucket={}, prefix='{}')",
				properties.getProvider(),
				bucket,
				keyPrefix
		);
	}

	@Override
	public String store(
			UUID ownerId,
			UUID workspaceId,
			UUID documentId,
			String safeFilename,
			InputStream content,
			long contentLength
	) {
		String relativeKey = ownerId + "/" + workspaceId + "/" + documentId + "/" + safeFilename;
		String objectKey = keyPrefix + relativeKey;

		// Buffer the stream so Content-Length matches bytes actually sent.
		// MultipartFile.getSize() can disagree with getInputStream() length, and B2/S3
		// reject short streams when chunked encoding is disabled (required for many S3-compat APIs).
		byte[] bytes;
		try {
			bytes = content.readAllBytes();
		}
		catch (IOException ex) {
			throw new StorageException("Failed to read upload stream for '" + objectKey + "'", ex);
		}

		if (bytes.length == 0) {
			throw new StorageException("Cannot store empty object: " + objectKey);
		}
		if (contentLength >= 0 && contentLength != bytes.length) {
			log.warn(
					"Upload size mismatch for {}: declared={}, actual={} — using actual",
					objectKey,
					contentLength,
					bytes.length
			);
		}

		try {
			PutObjectRequest request = PutObjectRequest.builder()
					.bucket(bucket)
					.key(objectKey)
					.contentLength((long) bytes.length)
					.build();
			s3Client.putObject(request, RequestBody.fromBytes(bytes));
			return relativeKey;
		}
		catch (S3Exception ex) {
			String detail = ex.awsErrorDetails() != null
					? ex.awsErrorDetails().errorMessage()
					: ex.getMessage();
			throw new StorageException("Failed to store object '" + objectKey + "': " + detail, ex);
		}
		catch (RuntimeException ex) {
			throw new StorageException("Failed to store object '" + objectKey + "': " + ex.getMessage(), ex);
		}
	}

	@Override
	public void deleteQuietly(String storagePath) {
		if (storagePath == null || storagePath.isBlank()) {
			return;
		}
		String objectKey = keyPrefix + normalizeRelative(storagePath);
		try {
			s3Client.deleteObject(DeleteObjectRequest.builder()
					.bucket(bucket)
					.key(objectKey)
					.build());
		}
		catch (Exception ex) {
			log.warn("Failed to delete object {}: {}", objectKey, ex.getMessage());
		}
	}

	@Override
	public InputStream open(String storagePath) {
		String objectKey = keyPrefix + normalizeRelative(storagePath);
		try {
			return s3Client.getObject(GetObjectRequest.builder()
					.bucket(bucket)
					.key(objectKey)
					.build());
		}
		catch (NoSuchKeyException ex) {
			throw new StorageException("Stored file not found: " + storagePath, ex);
		}
		catch (S3Exception ex) {
			if (ex.statusCode() == 404) {
				throw new StorageException("Stored file not found: " + storagePath, ex);
			}
			String detail = ex.awsErrorDetails() != null
					? ex.awsErrorDetails().errorMessage()
					: ex.getMessage();
			throw new StorageException("Failed to open object '" + objectKey + "': " + detail, ex);
		}
		catch (RuntimeException ex) {
			throw new StorageException("Failed to open object '" + objectKey + "': " + ex.getMessage(), ex);
		}
	}

	private static String normalizeRelative(String storagePath) {
		String key = storagePath.replace('\\', '/').trim();
		while (key.startsWith("/")) {
			key = key.substring(1);
		}
		if (key.isEmpty() || key.contains("..")) {
			throw new StorageException("Invalid storage path: " + storagePath);
		}
		return key;
	}

	private static String require(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new StorageException("Missing required storage config: " + name);
		}
		return value.trim();
	}
}
