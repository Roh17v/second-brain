package com.secondbrain.chat.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.secondbrain.chat.entity.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

	List<Conversation> findByWorkspaceIdAndOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
			UUID workspaceId,
			UUID ownerId
	);

	Optional<Conversation> findByIdAndWorkspaceIdAndOwnerIdAndDeletedAtIsNull(
			UUID id,
			UUID workspaceId,
			UUID ownerId
	);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update Conversation c
			set c.deletedAt = :now
			where c.workspaceId = :workspaceId
			  and c.deletedAt is null
			""")
	int softDeleteByWorkspaceId(@Param("workspaceId") UUID workspaceId, @Param("now") Instant now);
}
