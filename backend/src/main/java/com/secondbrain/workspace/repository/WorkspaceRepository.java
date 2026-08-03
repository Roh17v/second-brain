package com.secondbrain.workspace.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secondbrain.workspace.entity.Workspace;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

	List<Workspace> findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID ownerId);

	Optional<Workspace> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);
}
