package com.secondbrain.chat.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "message_citations")
public class MessageCitation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "message_id", nullable = false, updatable = false)
	private UUID messageId;

	@Column(name = "chunk_id", nullable = false, updatable = false)
	private UUID chunkId;

	@Column(name = "document_id", nullable = false, updatable = false)
	private UUID documentId;

	@Column(name = "chunk_index", nullable = false)
	private int chunkIndex;

	@Column(nullable = false)
	private double score;

	@Column(name = "source_filename", length = 500)
	private String sourceFilename;

	@Column(name = "snippet", nullable = false, columnDefinition = "TEXT")
	private String snippet;

	protected MessageCitation() {
	}

	public MessageCitation(
			UUID messageId,
			UUID chunkId,
			UUID documentId,
			int chunkIndex,
			double score,
			String sourceFilename,
			String snippet
	) {
		this.messageId = messageId;
		this.chunkId = chunkId;
		this.documentId = documentId;
		this.chunkIndex = chunkIndex;
		this.score = score;
		this.sourceFilename = sourceFilename;
		this.snippet = snippet;
	}

	public UUID getId() {
		return id;
	}

	public UUID getMessageId() {
		return messageId;
	}

	public UUID getChunkId() {
		return chunkId;
	}

	public UUID getDocumentId() {
		return documentId;
	}

	public int getChunkIndex() {
		return chunkIndex;
	}

	public double getScore() {
		return score;
	}

	public String getSourceFilename() {
		return sourceFilename;
	}

	public String getSnippet() {
		return snippet;
	}
}
