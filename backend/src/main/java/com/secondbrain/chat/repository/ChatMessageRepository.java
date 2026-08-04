package com.secondbrain.chat.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secondbrain.chat.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

	List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
