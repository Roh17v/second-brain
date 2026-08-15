package com.secondbrain.document.mapper;

import org.springframework.stereotype.Component;

import com.secondbrain.document.dto.DocumentResponse;
import com.secondbrain.document.entity.Document;
import com.secondbrain.document.entity.DocumentStatus;
import com.secondbrain.document.service.IngestEta;
import com.secondbrain.search.EmbedSpeedTracker;

@Component
public class DocumentMapper {

	private final EmbedSpeedTracker embedSpeedTracker;

	public DocumentMapper(EmbedSpeedTracker embedSpeedTracker) {
		this.embedSpeedTracker = embedSpeedTracker;
	}

	public DocumentResponse toResponse(Document document) {
		Integer eta = null;
		if (document.getStatus() == DocumentStatus.EMBEDDING) {
			eta = IngestEta.remainingSeconds(
					document.getChunkCount(),
					document.getEmbeddedCount(),
					embedSpeedTracker.averageMs()
			);
		}
		boolean inFlight = document.getStatus() == DocumentStatus.UPLOADED
				|| document.getStatus() == DocumentStatus.PROCESSING
				|| document.getStatus() == DocumentStatus.EMBEDDING;
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
				document.getUpdatedAt(),
				document.getChunkCount(),
				document.getEmbeddedCount(),
				eta,
				document.isNotifyOnReady() && inFlight
		);
	}
}
