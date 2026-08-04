package com.secondbrain.chat.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
