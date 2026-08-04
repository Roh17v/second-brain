package com.secondbrain.chat.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secondbrain.ai.llm.LlmClient;
import com.secondbrain.ai.llm.LlmMessage;
import com.secondbrain.chat.dto.ChatAnswerResponse;
import com.secondbrain.chat.dto.ChatMessageResponse;
import com.secondbrain.chat.dto.CitationResponse;
import com.secondbrain.chat.dto.ConversationDetailResponse;
import com.secondbrain.chat.dto.ConversationResponse;
import com.secondbrain.chat.dto.CreateConversationRequest;
import com.secondbrain.chat.dto.SendMessageRequest;
import com.secondbrain.chat.entity.ChatMessage;
import com.secondbrain.chat.entity.Conversation;
import com.secondbrain.chat.entity.MessageCitation;
import com.secondbrain.chat.entity.MessageRole;
import com.secondbrain.chat.repository.ChatMessageRepository;
import com.secondbrain.chat.repository.ConversationRepository;
import com.secondbrain.chat.repository.MessageCitationRepository;
import com.secondbrain.common.exception.BadRequestException;
import com.secondbrain.common.exception.ResourceNotFoundException;
import com.secondbrain.document.entity.Document;
import com.secondbrain.document.repository.DocumentRepository;
import com.secondbrain.search.EmbeddingService;
import com.secondbrain.search.SearchHitResponse;
import com.secondbrain.security.SecurityUtils;
import com.secondbrain.security.UserPrincipal;
import com.secondbrain.workspace.service.WorkspaceService;

@Service
public class ChatService {

	private static final int DEFAULT_TOP_K = 5;
	private static final int MAX_HISTORY_MESSAGES = 12;

	private final WorkspaceService workspaceService;
	private final ConversationRepository conversationRepository;
	private final ChatMessageRepository messageRepository;
	private final MessageCitationRepository citationRepository;
	private final EmbeddingService embeddingService;
	private final DocumentRepository documentRepository;
	private final LlmClient llmClient;
	private final RagPromptBuilder promptBuilder;

	public ChatService(
			WorkspaceService workspaceService,
			ConversationRepository conversationRepository,
			ChatMessageRepository messageRepository,
			MessageCitationRepository citationRepository,
			EmbeddingService embeddingService,
			DocumentRepository documentRepository,
			LlmClient llmClient,
			RagPromptBuilder promptBuilder
	) {
		this.workspaceService = workspaceService;
		this.conversationRepository = conversationRepository;
		this.messageRepository = messageRepository;
		this.citationRepository = citationRepository;
		this.embeddingService = embeddingService;
		this.documentRepository = documentRepository;
		this.llmClient = llmClient;
		this.promptBuilder = promptBuilder;
	}

	@Transactional
	public ConversationResponse createConversation(UUID workspaceId, CreateConversationRequest request) {
		workspaceService.requireOwnedWorkspace(workspaceId);
		UserPrincipal user = SecurityUtils.requireCurrentUser();

		String title = request != null && request.title() != null && !request.title().isBlank()
				? request.title().trim()
				: "New conversation";

		Conversation conversation = new Conversation(workspaceId, user.getId(), title);
		return toConversationResponse(conversationRepository.save(conversation));
	}

	@Transactional(readOnly = true)
	public List<ConversationResponse> listConversations(UUID workspaceId) {
		workspaceService.requireOwnedWorkspace(workspaceId);
		UserPrincipal user = SecurityUtils.requireCurrentUser();
		return conversationRepository
				.findByWorkspaceIdAndOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(workspaceId, user.getId())
				.stream()
				.map(this::toConversationResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public ConversationDetailResponse getConversation(UUID workspaceId, UUID conversationId) {
		Conversation conversation = requireOwnedConversation(workspaceId, conversationId);
		List<ChatMessage> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);

		List<ChatMessageResponse> messageResponses = new ArrayList<>();
		for (ChatMessage message : messages) {
			List<CitationResponse> citations = List.of();
			if (message.getRole() == MessageRole.ASSISTANT) {
				List<MessageCitation> stored = citationRepository.findByMessageIdOrderByScoreDesc(message.getId());
				List<CitationResponse> mapped = new ArrayList<>();
				for (int i = 0; i < stored.size(); i++) {
					mapped.add(toCitationResponse(stored.get(i), i + 1));
				}
				citations = mapped;
			}
			messageResponses.add(new ChatMessageResponse(
					message.getId(),
					message.getRole(),
					message.getContent(),
					message.getCreatedAt(),
					citations
			));
		}
		return new ConversationDetailResponse(toConversationResponse(conversation), messageResponses);
	}

	@Transactional
	public ChatAnswerResponse sendMessage(UUID workspaceId, UUID conversationId, SendMessageRequest request) {
		Conversation conversation = requireOwnedConversation(workspaceId, conversationId);

		String userText = request.message().trim();
		if (userText.isEmpty()) {
			throw new BadRequestException("message must not be blank");
		}

		int topK = request.topK() == null ? DEFAULT_TOP_K : request.topK();

		// Auto-title from first user message
		if ("New conversation".equals(conversation.getTitle())) {
			String title = userText.length() > 80 ? userText.substring(0, 80) + "..." : userText;
			conversation.setTitle(title);
		}

		ChatMessage userMessage = messageRepository.save(
				new ChatMessage(conversation.getId(), MessageRole.USER, userText)
		);

		List<SearchHitResponse> hits = embeddingService.search(workspaceId, userText, topK);
		List<String> filenames = resolveFilenames(hits);
		RagPromptBuilder.BuiltPrompt built = promptBuilder.build(userText, hits, filenames);

		List<ChatMessage> history = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
		List<LlmMessage> llmMessages = toLlmHistory(history, built.userPrompt());

		String answer = llmClient.chat(built.systemPrompt(), llmMessages);

		ChatMessage assistantMessage = messageRepository.save(
				new ChatMessage(conversation.getId(), MessageRole.ASSISTANT, answer)
		);

		List<MessageCitation> savedCitations = new ArrayList<>();
		for (CitationResponse citation : built.citations()) {
			savedCitations.add(citationRepository.save(new MessageCitation(
					assistantMessage.getId(),
					citation.chunkId(),
					citation.documentId(),
					citation.chunkIndex(),
					citation.score(),
					citation.sourceFilename(),
					citation.snippet()
			)));
		}

		conversationRepository.save(conversation);

		// Keep citation numbers aligned with the prompt ([1], [2], ...)
		List<CitationResponse> citationResponses = built.citations();

		return new ChatAnswerResponse(
				conversation.getId(),
				new ChatMessageResponse(
						userMessage.getId(),
						userMessage.getRole(),
						userMessage.getContent(),
						userMessage.getCreatedAt(),
						List.of()
				),
				new ChatMessageResponse(
						assistantMessage.getId(),
						assistantMessage.getRole(),
						assistantMessage.getContent(),
						assistantMessage.getCreatedAt(),
						citationResponses
				),
				citationResponses,
				llmClient.modelId()
		);
	}

	@Transactional
	public void softDeleteConversation(UUID workspaceId, UUID conversationId) {
		Conversation conversation = requireOwnedConversation(workspaceId, conversationId);
		conversation.softDelete();
		conversationRepository.save(conversation);
	}

	private List<LlmMessage> toLlmHistory(List<ChatMessage> history, String currentUserPromptWithContext) {
		// history includes the user message just saved; rebuild prior turns only, then current RAG user prompt
		List<ChatMessage> prior = history.size() <= 1
				? List.of()
				: history.subList(0, history.size() - 1);

		int from = Math.max(0, prior.size() - MAX_HISTORY_MESSAGES);
		List<LlmMessage> messages = new ArrayList<>();
		for (ChatMessage message : prior.subList(from, prior.size())) {
			if (message.getRole() == MessageRole.USER) {
				messages.add(LlmMessage.user(message.getContent()));
			}
			else if (message.getRole() == MessageRole.ASSISTANT) {
				messages.add(LlmMessage.assistant(message.getContent()));
			}
		}
		messages.add(LlmMessage.user(currentUserPromptWithContext));
		return messages;
	}

	private List<String> resolveFilenames(List<SearchHitResponse> hits) {
		Map<UUID, String> cache = new HashMap<>();
		List<String> names = new ArrayList<>();
		for (SearchHitResponse hit : hits) {
			String name = cache.computeIfAbsent(hit.documentId(), id ->
					documentRepository.findById(id)
							.map(Document::getOriginalFilename)
							.orElse("document")
			);
			names.add(name);
		}
		return names;
	}

	private Conversation requireOwnedConversation(UUID workspaceId, UUID conversationId) {
		workspaceService.requireOwnedWorkspace(workspaceId);
		UserPrincipal user = SecurityUtils.requireCurrentUser();
		return conversationRepository
				.findByIdAndWorkspaceIdAndOwnerIdAndDeletedAtIsNull(conversationId, workspaceId, user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));
	}

	private ConversationResponse toConversationResponse(Conversation conversation) {
		return new ConversationResponse(
				conversation.getId(),
				conversation.getWorkspaceId(),
				conversation.getTitle(),
				conversation.getCreatedAt(),
				conversation.getUpdatedAt()
		);
	}

	private CitationResponse toCitationResponse(MessageCitation citation, int index) {
		return new CitationResponse(
				index,
				citation.getChunkId(),
				citation.getDocumentId(),
				citation.getSourceFilename(),
				citation.getChunkIndex(),
				citation.getScore(),
				citation.getSnippet()
		);
	}
}
