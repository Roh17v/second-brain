package com.secondbrain.document.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.secondbrain.document.entity.Document;
import com.secondbrain.document.entity.DocumentStatus;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

	List<Document> findByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID workspaceId);

	Optional<Document> findByIdAndWorkspaceIdAndDeletedAtIsNull(UUID id, UUID workspaceId);

	Optional<Document> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);

	long countByOwnerIdAndDeletedAtIsNull(UUID ownerId);

	long countByOwnerIdAndDeletedAtIsNullAndStatus(UUID ownerId, DocumentStatus status);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update Document d
			set d.deletedAt = :now
			where d.workspaceId = :workspaceId
			  and d.deletedAt is null
			""")
	int softDeleteByWorkspaceId(@Param("workspaceId") UUID workspaceId, @Param("now") Instant now);

	/**
	 * Status-only write that will not revive a concurrently soft-deleted row
	 * (full entity {@code save} would write {@code deleted_at = null}).
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update Document d
			set d.status = :status, d.failureReason = :reason
			where d.id = :id
			  and d.deletedAt is null
			""")
	int updateStatusIfNotDeleted(
			@Param("id") UUID id,
			@Param("status") DocumentStatus status,
			@Param("reason") String reason
	);
}
