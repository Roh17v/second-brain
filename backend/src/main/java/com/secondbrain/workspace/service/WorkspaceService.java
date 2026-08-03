package com.secondbrain.workspace.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secondbrain.common.exception.ResourceNotFoundException;
import com.secondbrain.security.SecurityUtils;
import com.secondbrain.security.UserPrincipal;
import com.secondbrain.workspace.dto.CreateWorkspaceRequest;
import com.secondbrain.workspace.dto.UpdateWorkspaceRequest;
import com.secondbrain.workspace.dto.WorkspaceResponse;
import com.secondbrain.workspace.entity.Workspace;
import com.secondbrain.workspace.mapper.WorkspaceMapper;
import com.secondbrain.workspace.repository.WorkspaceRepository;

@Service
public class WorkspaceService {

	private final WorkspaceRepository workspaceRepository;
	private final WorkspaceMapper workspaceMapper;

	public WorkspaceService(WorkspaceRepository workspaceRepository, WorkspaceMapper workspaceMapper) {
		this.workspaceRepository = workspaceRepository;
		this.workspaceMapper = workspaceMapper;
	}

	@Transactional
	public WorkspaceResponse create(CreateWorkspaceRequest request) {
		UserPrincipal currentUser = SecurityUtils.requireCurrentUser();
		Workspace workspace = workspaceMapper.toEntity(request, currentUser.getId());

		if (workspace.getName().isEmpty()) {
			throw new IllegalArgumentException("name must not be blank");
		}

		Workspace saved = workspaceRepository.save(workspace);
		return workspaceMapper.toResponse(saved);
	}

	@Transactional(readOnly = true)
	public List<WorkspaceResponse> listMine() {
		UserPrincipal currentUser = SecurityUtils.requireCurrentUser();
		return workspaceRepository
				.findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(currentUser.getId())
				.stream()
				.map(workspaceMapper::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public WorkspaceResponse getMineById(UUID id) {
		Workspace workspace = requireOwnedWorkspace(id);
		return workspaceMapper.toResponse(workspace);
	}

	@Transactional
	public WorkspaceResponse updateMine(UUID id, UpdateWorkspaceRequest request) {
		Workspace workspace = requireOwnedWorkspace(id);

		if (request.name() != null) {
			String name = request.name().trim();
			if (name.isEmpty()) {
				throw new IllegalArgumentException("name must not be blank");
			}
			workspace.setName(name);
		}

		if (request.description() != null) {
			workspace.setDescription(workspaceMapper.normalizeDescription(request.description()));
		}

		Workspace saved = workspaceRepository.save(workspace);
		return workspaceMapper.toResponse(saved);
	}

	@Transactional
	public void softDeleteMine(UUID id) {
		Workspace workspace = requireOwnedWorkspace(id);
		workspace.softDelete();
		workspaceRepository.save(workspace);
	}

	private Workspace requireOwnedWorkspace(UUID id) {
		UserPrincipal currentUser = SecurityUtils.requireCurrentUser();
		return workspaceRepository
				.findByIdAndOwnerIdAndDeletedAtIsNull(id, currentUser.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found: " + id));
	}
}
