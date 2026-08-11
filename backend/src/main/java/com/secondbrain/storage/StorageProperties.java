package com.secondbrain.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * File storage settings. Swap backend with {@code app.storage.provider} only.
 */
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

	/**
	 * {@link StorageProviders#LOCAL}, {@link StorageProviders#B2}, or {@link StorageProviders#S3}.
	 */
	private String provider = StorageProviders.LOCAL;

	/**
	 * Root directory for local provider (relative or absolute).
	 */
	private String basePath = "./storage";

	/**
	 * Optional key prefix inside the bucket (e.g. {@code prod/} or {@code secondbrain/}).
	 * Applied to all object keys. Trailing slash added if missing when non-empty.
	 */
	private String keyPrefix = "";

	/** S3-compatible bucket name. */
	private String bucket = "";

	/**
	 * Service endpoint, e.g. {@code https://s3.us-west-004.backblazeb2.com}
	 * or {@code https://s3.amazonaws.com}.
	 */
	private String endpoint = "";

	/** Region string required by the SDK (B2 uses its region id, e.g. {@code us-west-004}). */
	private String region = "us-west-004";

	/** Access key id (B2 application key id). */
	private String accessKeyId = "";

	/** Secret key (B2 application key). */
	private String secretAccessKey = "";

	/**
	 * Force path-style URLs ({@code endpoint/bucket/key}). Recommended for B2 and MinIO.
	 */
	private boolean pathStyleAccess = true;

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getBasePath() {
		return basePath;
	}

	public void setBasePath(String basePath) {
		this.basePath = basePath;
	}

	public String getKeyPrefix() {
		return keyPrefix;
	}

	public void setKeyPrefix(String keyPrefix) {
		this.keyPrefix = keyPrefix;
	}

	public String getBucket() {
		return bucket;
	}

	public void setBucket(String bucket) {
		this.bucket = bucket;
	}

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getAccessKeyId() {
		return accessKeyId;
	}

	public void setAccessKeyId(String accessKeyId) {
		this.accessKeyId = accessKeyId;
	}

	public String getSecretAccessKey() {
		return secretAccessKey;
	}

	public void setSecretAccessKey(String secretAccessKey) {
		this.secretAccessKey = secretAccessKey;
	}

	public boolean isPathStyleAccess() {
		return pathStyleAccess;
	}

	public void setPathStyleAccess(boolean pathStyleAccess) {
		this.pathStyleAccess = pathStyleAccess;
	}

	public boolean isObjectStorage() {
		String p = provider == null ? "" : provider.trim().toLowerCase();
		return StorageProviders.B2.equals(p) || StorageProviders.S3.equals(p);
	}

	/**
	 * Relative object key prefix with trailing slash, or empty.
	 */
	public String normalizedKeyPrefix() {
		if (keyPrefix == null || keyPrefix.isBlank()) {
			return "";
		}
		String p = keyPrefix.trim().replace('\\', '/');
		while (p.startsWith("/")) {
			p = p.substring(1);
		}
		if (!p.isEmpty() && !p.endsWith("/")) {
			p = p + "/";
		}
		return p;
	}
}
