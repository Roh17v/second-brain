package com.secondbrain.workspace.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.secondbrain.workspace.dto.CreateWorkspaceRequest;
import com.secondbrain.workspace.dto.UpdateWorkspaceRequest;
import com.secondbrain.workspace.dto.WorkspaceResponse;
import com.secondbrain.workspace.service.WorkspaceService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

	private final WorkspaceService workspaceService;

	public WorkspaceController(WorkspaceService workspaceService) {
		this.workspaceService = workspaceService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public WorkspaceResponse create(@Valid @RequestBody CreateWorkspaceRequest request) {
		return workspaceService.create(request);
	}

	@GetMapping
	public List<WorkspaceResponse> listMine() {
		return workspaceService.listMine();
	}

	@GetMapping("/{id}")
	public WorkspaceResponse getMine(@PathVariable UUID id) {
		return workspaceService.getMineById(id);
	}

	@PutMapping("/{id}")
	public WorkspaceResponse updateMine(
			@PathVariable UUID id,
			@Valid @RequestBody UpdateWorkspaceRequest request
	) {
		return workspaceService.updateMine(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMine(@PathVariable UUID id) {
		workspaceService.softDeleteMine(id);
	}
}
