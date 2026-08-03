package com.secondbrain.workspace.entity;

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
@Table(name = "workspaces")
public class Workspace {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(length = 1000)
	private String description;

	@Column(name = "owner_id", nullable = false, updatable = false)
	private UUID ownerId;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected Workspace() {
		// JPA
	}

	public Workspace(String name, String description, UUID ownerId) {
		this.name = name;
		this.description = description;
		this.ownerId = ownerId;
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

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public UUID getOwnerId() {
		return ownerId;
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
