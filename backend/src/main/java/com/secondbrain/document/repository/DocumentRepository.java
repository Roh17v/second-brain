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

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			update documents
			set status = 'PROCESSING',
			    failure_reason = null,
			    processing_started_at = :startedAt,
			    chunk_count = null,
			    embedded_count = 0,
			    notify_on_ready = false,
			    ready_notified_at = null
			where id = :id
			  and deleted_at is null
			""", nativeQuery = true)
	int markProcessingStarted(@Param("id") UUID id, @Param("startedAt") Instant startedAt);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			update documents
			set status = 'EMBEDDING',
			    failure_reason = null,
			    chunk_count = :chunkCount,
			    notify_on_ready = (notify_on_ready or :notifyOnReady)
			where id = :id
			  and deleted_at is null
			""", nativeQuery = true)
	int markEmbedding(
			@Param("id") UUID id,
			@Param("chunkCount") int chunkCount,
			@Param("notifyOnReady") boolean notifyOnReady
	);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update Document d
			set d.embeddedCount = :embeddedCount
			where d.id = :id
			  and d.deletedAt is null
			""")
	int updateEmbeddedCount(@Param("id") UUID id, @Param("embeddedCount") int embeddedCount);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update Document d
			set d.notifyOnReady = true
			where d.id = :id
			  and d.deletedAt is null
			  and d.notifyOnReady = false
			""")
	int markNotifyOnReady(@Param("id") UUID id);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update Document d
			set d.readyNotifiedAt = :at
			where d.id = :id
			  and d.deletedAt is null
			  and d.readyNotifiedAt is null
			""")
	int markReadyNotified(@Param("id") UUID id, @Param("at") Instant at);
}
