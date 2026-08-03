package com.secondbrain.storage;

import java.io.InputStream;
import java.util.UUID;

public interface FileStorageService {

	/**
	 * Stores a file and returns the relative storage key/path used to retrieve it later.
	 */
	String store(
			UUID ownerId,
			UUID workspaceId,
			UUID documentId,
			String safeFilename,
			InputStream content,
			long contentLength
	);

	void deleteQuietly(String storagePath);

	/**
	 * Opens a stored file for reading. Caller must close the stream.
	 */
	InputStream open(String storagePath);
}
