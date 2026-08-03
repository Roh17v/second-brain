package com.secondbrain.search;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
public class SearchController {

	private final EmbeddingService embeddingService;

	public SearchController(EmbeddingService embeddingService) {
		this.embeddingService = embeddingService;
	}

	@PostMapping("/documents/{documentId}/embed")
	public DocumentEmbedResponse embedDocument(
			@PathVariable UUID workspaceId,
			@PathVariable UUID documentId
	) {
		return embeddingService.embedDocument(workspaceId, documentId);
	}

	@PostMapping("/search")
	public List<SearchHitResponse> search(
			@PathVariable UUID workspaceId,
			@Valid @RequestBody SearchRequest request
	) {
		int topK = request.topK() == null ? 5 : request.topK();
		return embeddingService.search(workspaceId, request.query(), topK);
	}
}
