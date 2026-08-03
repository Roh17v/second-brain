package com.secondbrain.document.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secondbrain.document.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

	List<Document> findByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID workspaceId);

	Optional<Document> findByIdAndWorkspaceIdAndDeletedAtIsNull(UUID id, UUID workspaceId);

	Optional<Document> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);
}
