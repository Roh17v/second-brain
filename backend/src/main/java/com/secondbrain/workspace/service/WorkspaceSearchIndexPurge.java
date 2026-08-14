package com.secondbrain.workspace.service;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.secondbrain.chat.repository.ConversationRepository;
import com.secondbrain.document.repository.DocumentChunkRepository;
import com.secondbrain.document.repository.DocumentRepository;

/**
 * Removes searchable knowledge when a collection is soft-deleted.
 * Workspace / document / conversation rows stay (soft delete);
 * chunk rows (and their embeddings) are hard-deleted so they cannot
 * appear in vector or keyword retrieval.
 */
@Component
public class WorkspaceSearchIndexPurge {

	private static final Logger log = LoggerFactory.getLogger(WorkspaceSearchIndexPurge.class);

	private final DocumentRepository documentRepository;
	private final DocumentChunkRepository chunkRepository;
	private final ConversationRepository conversationRepository;

	public WorkspaceSearchIndexPurge(
			DocumentRepository documentRepository,
			DocumentChunkRepository chunkRepository,
			ConversationRepository conversationRepository
	) {
		this.documentRepository = documentRepository;
		this.chunkRepository = chunkRepository;
		this.conversationRepository = conversationRepository;
	}

	public record Result(int documentsSoftDeleted, int chunksRemoved, int conversationsSoftDeleted) {
	}

	/**
	 * If the document (or its collection) was deleted mid-ingest, drop any chunks
	 * just written so they cannot be embedded or retrieved.
	 *
	 * @return true when chunks were discarded
	 */
	@Transactional
	public boolean discardChunksIfNotSearchable(UUID workspaceId, UUID documentId) {
		boolean active = documentRepository
				.findByIdAndWorkspaceIdAndDeletedAtIsNull(documentId, workspaceId)
				.isPresent();
		if (active) {
			return false;
		}
		chunkRepository.deleteByDocumentId(documentId);
		log.info("Dropped chunks for document {} (document or collection deleted)", documentId);
		return true;
	}

	@Transactional
	public Result purge(UUID workspaceId) {
		Instant now = Instant.now();
		// Soft-delete documents first so in-flight ingest/embed loaders miss them.
		int documents = documentRepository.softDeleteByWorkspaceId(workspaceId, now);
		int chunks = chunkRepository.deleteByWorkspaceId(workspaceId);
		int conversations = conversationRepository.softDeleteByWorkspaceId(workspaceId, now);
		log.info(
				"Purged search index for workspace {}: documentsSoftDeleted={}, chunksRemoved={}, conversationsSoftDeleted={}",
				workspaceId,
				documents,
				chunks,
				conversations
		);
		return new Result(documents, chunks, conversations);
	}
}
