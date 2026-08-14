package com.secondbrain.document.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "document_chunks",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_document_chunk_index",
				columnNames = { "document_id", "chunk_index" }
		)
)
public class DocumentChunk {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "document_id", nullable = false, updatable = false)
	private UUID documentId;

	@Column(name = "workspace_id", nullable = false, updatable = false)
	private UUID workspaceId;

	@Column(name = "owner_id", nullable = false, updatable = false)
	private UUID ownerId;

	@Column(name = "chunk_index", nullable = false)
	private int chunkIndex;

	// Do NOT use @Lob with PostgreSQL — Hibernate may map it to OID and
	// native SQL will return large-object ids (e.g. "53634711") instead of text.
	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "content_length", nullable = false)
	private int contentLength;

	@Column(name = "section_heading", length = 400)
	private String sectionHeading;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected DocumentChunk() {
		// JPA
	}

	public DocumentChunk(
			UUID documentId,
			UUID workspaceId,
			UUID ownerId,
			int chunkIndex,
			String content
	) {
		this(documentId, workspaceId, ownerId, chunkIndex, content, null);
	}

	public DocumentChunk(
			UUID documentId,
			UUID workspaceId,
			UUID ownerId,
			int chunkIndex,
			String content,
			String sectionHeading
	) {
		this.documentId = documentId;
		this.workspaceId = workspaceId;
		this.ownerId = ownerId;
		this.chunkIndex = chunkIndex;
		this.content = content;
		this.contentLength = content.length();
		this.sectionHeading = sectionHeading;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getDocumentId() {
		return documentId;
	}

	public UUID getWorkspaceId() {
		return workspaceId;
	}

	public UUID getOwnerId() {
		return ownerId;
	}

	public int getChunkIndex() {
		return chunkIndex;
	}

	public String getContent() {
		return content;
	}

	public int getContentLength() {
		return contentLength;
	}

	public String getSectionHeading() {
		return sectionHeading;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
