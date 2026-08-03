package com.secondbrain.document.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "documents")
public class Document {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "workspace_id", nullable = false, updatable = false)
	private UUID workspaceId;

	@Column(name = "owner_id", nullable = false, updatable = false)
	private UUID ownerId;

	@Column(name = "original_filename", nullable = false, length = 500)
	private String originalFilename;

	@Column(name = "stored_filename", nullable = false, length = 500)
	private String storedFilename;

	@Column(name = "content_type", length = 200)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "storage_path", nullable = false, length = 1000)
	private String storagePath;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private DocumentStatus status;

	@Column(name = "failure_reason", length = 1000)
	private String failureReason;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected Document() {
		// JPA
	}

	public Document(
			UUID workspaceId,
			UUID ownerId,
			String originalFilename,
			String storedFilename,
			String contentType,
			long sizeBytes,
			String storagePath,
			DocumentStatus status
	) {
		this.workspaceId = workspaceId;
		this.ownerId = ownerId;
		this.originalFilename = originalFilename;
		this.storedFilename = storedFilename;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.storagePath = storagePath;
		this.status = status;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public void softDelete() {
		this.deletedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getWorkspaceId() {
		return workspaceId;
	}

	public UUID getOwnerId() {
		return ownerId;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public String getStoredFilename() {
		return storedFilename;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public String getStoragePath() {
		return storagePath;
	}

	public void setStoragePath(String storagePath) {
		this.storagePath = storagePath;
	}

	public DocumentStatus getStatus() {
		return status;
	}

	public void setStatus(DocumentStatus status) {
		this.status = status;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public void setFailureReason(String failureReason) {
		this.failureReason = failureReason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}
}
