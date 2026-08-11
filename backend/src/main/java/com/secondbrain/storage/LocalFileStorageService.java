package com.secondbrain.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.secondbrain.common.exception.StorageException;

/**
 * Local disk storage. Default when {@code app.storage.provider=local} (or unset).
 */
@Service
@ConditionalOnProperty(
		name = "app.storage.provider",
		havingValue = StorageProviders.LOCAL,
		matchIfMissing = true
)
public class LocalFileStorageService implements FileStorageService {

	private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

	private final Path root;

	public LocalFileStorageService(StorageProperties properties) {
		this.root = Path.of(properties.getBasePath()).toAbsolutePath().normalize();
		try {
			Files.createDirectories(this.root);
		}
		catch (IOException ex) {
			throw new StorageException("Could not initialize storage directory: " + this.root, ex);
		}
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
		Path relative = Path.of(
				ownerId.toString(),
				workspaceId.toString(),
				documentId.toString(),
				safeFilename
		);
		Path destination = root.resolve(relative).normalize();

		if (!destination.startsWith(root)) {
			throw new StorageException("Invalid storage path resolved outside root");
		}

		try {
			Files.createDirectories(destination.getParent());
			Files.copy(content, destination, StandardCopyOption.REPLACE_EXISTING);
			return relative.toString().replace('\\', '/');
		}
		catch (IOException ex) {
			throw new StorageException("Failed to store file: " + safeFilename, ex);
		}
	}

	@Override
	public void deleteQuietly(String storagePath) {
		if (storagePath == null || storagePath.isBlank()) {
			return;
		}
		try {
			Path target = resolveSafe(storagePath);
			Files.deleteIfExists(target);
		}
		catch (IOException ex) {
			log.warn("Failed to delete stored file {}: {}", storagePath, ex.getMessage());
		}
	}

	@Override
	public InputStream open(String storagePath) {
		try {
			Path target = resolveSafe(storagePath);
			if (!Files.exists(target) || !Files.isRegularFile(target)) {
				throw new StorageException("Stored file not found: " + storagePath);
			}
			return Files.newInputStream(target);
		}
		catch (IOException ex) {
			throw new StorageException("Failed to open stored file: " + storagePath, ex);
		}
	}

	private Path resolveSafe(String storagePath) {
		Path target = root.resolve(storagePath).normalize();
		if (!target.startsWith(root)) {
			throw new StorageException("Invalid storage path resolved outside root");
		}
		return target;
	}
}
