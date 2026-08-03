package com.secondbrain.workspace.mapper;

import org.springframework.stereotype.Component;

import com.secondbrain.workspace.dto.CreateWorkspaceRequest;
import com.secondbrain.workspace.dto.WorkspaceResponse;
import com.secondbrain.workspace.entity.Workspace;

@Component
public class WorkspaceMapper {

	public Workspace toEntity(CreateWorkspaceRequest request, java.util.UUID ownerId) {
		String name = request.name().trim();
		String description = normalizeDescription(request.description());
		return new Workspace(name, description, ownerId);
	}

	public WorkspaceResponse toResponse(Workspace workspace) {
		return new WorkspaceResponse(
				workspace.getId(),
				workspace.getName(),
				workspace.getDescription(),
				workspace.getOwnerId(),
				workspace.getCreatedAt(),
				workspace.getUpdatedAt()
		);
	}

	public String normalizeDescription(String description) {
		if (description == null) {
			return null;
		}
		String trimmed = description.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
