package com.secondbrain.chat.entity;

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
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "conversation_id", nullable = false, updatable = false)
	private UUID conversationId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MessageRole role;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected ChatMessage() {
	}

	public ChatMessage(UUID conversationId, MessageRole role, String content) {
		this.conversationId = conversationId;
		this.role = role;
		this.content = content;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getConversationId() {
		return conversationId;
	}

	public MessageRole getRole() {
		return role;
	}

	public String getContent() {
		return content;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
