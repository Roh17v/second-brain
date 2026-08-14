package com.secondbrain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secondbrain.document.entity.DocumentStatus;
import com.secondbrain.document.repository.DocumentChunkRepository;
import com.secondbrain.document.repository.DocumentRepository;
import com.secondbrain.security.SecurityUtils;
import com.secondbrain.user.dto.LibraryStatsResponse;
import com.secondbrain.workspace.repository.WorkspaceRepository;

@Service
public class LibraryStatsService {

	private final WorkspaceRepository workspaceRepository;
	private final DocumentRepository documentRepository;
	private final DocumentChunkRepository chunkRepository;

	public LibraryStatsService(
			WorkspaceRepository workspaceRepository,
			DocumentRepository documentRepository,
			DocumentChunkRepository chunkRepository
	) {
		this.workspaceRepository = workspaceRepository;
		this.documentRepository = documentRepository;
		this.chunkRepository = chunkRepository;
	}

	@Transactional(readOnly = true)
	public LibraryStatsResponse mine() {
		var user = SecurityUtils.requireCurrentUser();
		var ownerId = user.getId();
		long collections = workspaceRepository.countByOwnerIdAndDeletedAtIsNull(ownerId);
		long documents = documentRepository.countByOwnerIdAndDeletedAtIsNull(ownerId);
		long indexed = documentRepository.countByOwnerIdAndDeletedAtIsNullAndStatus(
				ownerId,
				DocumentStatus.READY
		);
		long chunks = chunkRepository.countByOwnerId(ownerId);
		return new LibraryStatsResponse(collections, documents, indexed, chunks);
	}
}
