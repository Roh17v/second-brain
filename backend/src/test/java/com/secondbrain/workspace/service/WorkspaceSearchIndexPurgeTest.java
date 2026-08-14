package com.secondbrain.workspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.secondbrain.chat.repository.ConversationRepository;
import com.secondbrain.document.repository.DocumentChunkRepository;
import com.secondbrain.document.repository.DocumentRepository;

@ExtendWith(MockitoExtension.class)
class WorkspaceSearchIndexPurgeTest {

	@Mock
	DocumentRepository documentRepository;
	@Mock
	DocumentChunkRepository chunkRepository;
	@Mock
	ConversationRepository conversationRepository;

	WorkspaceSearchIndexPurge purge;

	@BeforeEach
	void setUp() {
		purge = new WorkspaceSearchIndexPurge(
				documentRepository,
				chunkRepository,
				conversationRepository
		);
	}

	@Test
	void purgeSoftDeletesDocsAndHardDeletesChunks() {
		UUID workspaceId = UUID.randomUUID();
		when(documentRepository.softDeleteByWorkspaceId(eq(workspaceId), any())).thenReturn(2);
		when(chunkRepository.deleteByWorkspaceId(workspaceId)).thenReturn(17);
		when(conversationRepository.softDeleteByWorkspaceId(eq(workspaceId), any())).thenReturn(3);

		WorkspaceSearchIndexPurge.Result result = purge.purge(workspaceId);

		assertEquals(2, result.documentsSoftDeleted());
		assertEquals(17, result.chunksRemoved());
		assertEquals(3, result.conversationsSoftDeleted());
		verify(chunkRepository).deleteByWorkspaceId(workspaceId);
	}

	@Test
	void discardChunksWhenDocumentNoLongerActive() {
		UUID workspaceId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		when(documentRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(documentId, workspaceId))
				.thenReturn(Optional.empty());

		assertTrue(purge.discardChunksIfNotSearchable(workspaceId, documentId));
		verify(chunkRepository).deleteByDocumentId(documentId);
	}

	@Test
	void keepsChunksWhenDocumentStillActive() {
		UUID workspaceId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		when(documentRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(documentId, workspaceId))
				.thenReturn(Optional.of(org.mockito.Mockito.mock(
						com.secondbrain.document.entity.Document.class
				)));

		assertFalse(purge.discardChunksIfNotSearchable(workspaceId, documentId));
		verify(chunkRepository, never()).deleteByDocumentId(any());
	}
}
