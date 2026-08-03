package com.secondbrain.document.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secondbrain.document.entity.DocumentChunk;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

	List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

	long countByDocumentId(UUID documentId);

	void deleteByDocumentId(UUID documentId);
}
