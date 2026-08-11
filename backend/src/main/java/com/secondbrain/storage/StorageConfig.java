package com.secondbrain.storage;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.secondbrain.common.exception.StorageException;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

	private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

	/**
	 * S3 client for B2 / generic S3-compatible stores.
	 * Closed automatically by Spring (AutoCloseable).
	 */
	@Bean(destroyMethod = "close")
	@ConditionalOnExpression(
			"T(java.util.Arrays).asList('b2','s3').contains('${app.storage.provider:local}'.toLowerCase())"
	)
	public S3Client s3Client(StorageProperties properties) {
		String endpoint = require(properties.getEndpoint(), "app.storage.endpoint / STORAGE_ENDPOINT");
		String region = require(properties.getRegion(), "app.storage.region / STORAGE_REGION");
		String accessKey = require(properties.getAccessKeyId(), "app.storage.access-key-id / STORAGE_ACCESS_KEY_ID");
		String secretKey = require(
				properties.getSecretAccessKey(),
				"app.storage.secret-access-key / STORAGE_SECRET_ACCESS_KEY"
		);

		URI endpointUri;
		try {
			endpointUri = URI.create(endpoint.trim());
		}
		catch (IllegalArgumentException ex) {
			throw new StorageException("Invalid storage endpoint URL: " + endpoint, ex);
		}

		S3Configuration s3Config = S3Configuration.builder()
				.pathStyleAccessEnabled(properties.isPathStyleAccess())
				.chunkedEncodingEnabled(false) // friendlier for some S3-compatible APIs (incl. B2)
				.build();

		log.info(
				"Creating S3 client (provider={}, endpoint={}, region={}, pathStyle={})",
				properties.getProvider(),
				endpointUri,
				region,
				properties.isPathStyleAccess()
		);

		return S3Client.builder()
				.endpointOverride(endpointUri)
				.region(Region.of(region.trim()))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(accessKey.trim(), secretKey.trim())
				))
				.serviceConfiguration(s3Config)
				.build();
	}

	private static String require(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new StorageException("Missing required storage config: " + name);
		}
		return value;
	}
}
