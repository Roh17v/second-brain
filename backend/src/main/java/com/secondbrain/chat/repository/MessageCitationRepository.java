package com.secondbrain.chat.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.secondbrain.chat.entity.MessageCitation;

public interface MessageCitationRepository extends JpaRepository<MessageCitation, UUID> {

	List<MessageCitation> findByMessageIdOrderByScoreDesc(UUID messageId);
}
