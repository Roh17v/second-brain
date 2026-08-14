package com.secondbrain.document.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.secondbrain.document.entity.DocumentChunk;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

	List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

	long countByDocumentId(UUID documentId);

	long countByOwnerId(UUID ownerId);

	void deleteByDocumentId(UUID documentId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from DocumentChunk c where c.workspaceId = :workspaceId")
	int deleteByWorkspaceId(@Param("workspaceId") UUID workspaceId);
}
