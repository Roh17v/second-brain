package com.secondbrain.chat.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "conversations")
public class Conversation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "workspace_id", nullable = false, updatable = false)
	private UUID workspaceId;

	@Column(name = "owner_id", nullable = false, updatable = false)
	private UUID ownerId;

	@Column(nullable = false, length = 300)
	private String title;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected Conversation() {
	}

	public Conversation(UUID workspaceId, UUID ownerId, String title) {
		this.workspaceId = workspaceId;
		this.ownerId = ownerId;
		this.title = title;
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

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
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
