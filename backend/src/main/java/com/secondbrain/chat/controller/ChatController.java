package com.secondbrain.chat.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.secondbrain.chat.dto.ChatAnswerResponse;
import com.secondbrain.chat.dto.ConversationDetailResponse;
import com.secondbrain.chat.dto.ConversationResponse;
import com.secondbrain.chat.dto.CreateConversationRequest;
import com.secondbrain.chat.dto.SendMessageRequest;
import com.secondbrain.chat.service.ChatService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/conversations")
public class ChatController {

	private final ChatService chatService;

	public ChatController(ChatService chatService) {
		this.chatService = chatService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ConversationResponse create(
			@PathVariable UUID workspaceId,
			@Valid @RequestBody(required = false) CreateConversationRequest request
	) {
		return chatService.createConversation(
				workspaceId,
				request == null ? new CreateConversationRequest(null) : request
		);
	}

	@GetMapping
	public List<ConversationResponse> list(@PathVariable UUID workspaceId) {
		return chatService.listConversations(workspaceId);
	}

	@GetMapping("/{conversationId}")
	public ConversationDetailResponse get(
			@PathVariable UUID workspaceId,
			@PathVariable UUID conversationId
	) {
		return chatService.getConversation(workspaceId, conversationId);
	}

	@PostMapping("/{conversationId}/messages")
	public ChatAnswerResponse sendMessage(
			@PathVariable UUID workspaceId,
			@PathVariable UUID conversationId,
			@Valid @RequestBody SendMessageRequest request
	) {
		return chatService.sendMessage(workspaceId, conversationId, request);
	}

	/**
	 * Streaming RAG answer (Server-Sent Events).
	 * Events: user, token, done, error.
	 */
	@PostMapping(
			value = "/{conversationId}/messages/stream",
			produces = MediaType.TEXT_EVENT_STREAM_VALUE
	)
	public SseEmitter streamMessage(
			@PathVariable UUID workspaceId,
			@PathVariable UUID conversationId,
			@Valid @RequestBody SendMessageRequest request
	) {
		return chatService.streamMessage(workspaceId, conversationId, request);
	}

	@DeleteMapping("/{conversationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(
			@PathVariable UUID workspaceId,
			@PathVariable UUID conversationId
	) {
		chatService.softDeleteConversation(workspaceId, conversationId);
	}
}

