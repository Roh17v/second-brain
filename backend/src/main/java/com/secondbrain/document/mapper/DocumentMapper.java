package com.secondbrain.document.mapper;

import org.springframework.stereotype.Component;

import com.secondbrain.document.dto.DocumentResponse;
import com.secondbrain.document.entity.Document;

@Component
public class DocumentMapper {

	public DocumentResponse toResponse(Document document) {
		return new DocumentResponse(
				document.getId(),
				document.getWorkspaceId(),
				document.getOwnerId(),
				document.getOriginalFilename(),
				document.getContentType(),
				document.getSizeBytes(),
				document.getStatus(),
				document.getFailureReason(),
				document.getCreatedAt(),
				document.getUpdatedAt()
		);
	}
}
