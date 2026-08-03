package com.secondbrain.document.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.secondbrain.document.dto.DocumentChunkResponse;
import com.secondbrain.document.dto.DocumentResponse;
import com.secondbrain.document.service.DocumentIngestionService;
import com.secondbrain.document.service.DocumentService;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/documents")
public class DocumentController {

	private final DocumentService documentService;
	private final DocumentIngestionService documentIngestionService;

	public DocumentController(
			DocumentService documentService,
			DocumentIngestionService documentIngestionService
	) {
		this.documentService = documentService;
		this.documentIngestionService = documentIngestionService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public DocumentResponse upload(
			@PathVariable UUID workspaceId,
			@RequestPart("file") MultipartFile file
	) {
		return documentService.upload(workspaceId, file);
	}

	@GetMapping
	public List<DocumentResponse> list(@PathVariable UUID workspaceId) {
		return documentService.listByWorkspace(workspaceId);
	}

	@GetMapping("/{documentId}")
	public DocumentResponse get(
			@PathVariable UUID workspaceId,
			@PathVariable UUID documentId
	) {
		return documentService.getById(workspaceId, documentId);
	}

	/**
	 * Parse file text, chunk it, and store chunks. Does not create embeddings yet.
	 */
	@PostMapping("/{documentId}/process")
	public DocumentResponse process(
			@PathVariable UUID workspaceId,
			@PathVariable UUID documentId
	) {
		return documentIngestionService.process(workspaceId, documentId);
	}

	@GetMapping("/{documentId}/chunks")
	public List<DocumentChunkResponse> listChunks(
			@PathVariable UUID workspaceId,
			@PathVariable UUID documentId
	) {
		return documentIngestionService.listChunks(workspaceId, documentId);
	}

	@DeleteMapping("/{documentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(
			@PathVariable UUID workspaceId,
			@PathVariable UUID documentId
	) {
		documentService.softDelete(workspaceId, documentId);
	}
}
