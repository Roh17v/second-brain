package com.secondbrain.chat.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
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

	private static final Logger log = LoggerFactory.getLogger(ChatService.class);

	private static final int DEFAULT_TOP_K = 5;
	private static final int MAX_HISTORY_MESSAGES = 12;
	private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L;

	private final WorkspaceService workspaceService;
	private final ConversationRepository conversationRepository;
	private final ChatMessageRepository messageRepository;
	private final MessageCitationRepository citationRepository;
	private final EmbeddingService embeddingService;
	private final DocumentRepository documentRepository;
	private final LlmClient llmClient;
	private final RagPromptBuilder promptBuilder;
	private final ContextualQueryService contextualQueryService;
	private final TransactionTemplate transactionTemplate;
	private final ObjectMapper objectMapper;
	private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

	public ChatService(
			WorkspaceService workspaceService,
			ConversationRepository conversationRepository,
			ChatMessageRepository messageRepository,
			MessageCitationRepository citationRepository,
			EmbeddingService embeddingService,
			DocumentRepository documentRepository,
			LlmClient llmClient,
			RagPromptBuilder promptBuilder,
			ContextualQueryService contextualQueryService,
			PlatformTransactionManager transactionManager,
			ObjectMapper objectMapper
	) {
		this.workspaceService = workspaceService;
		this.conversationRepository = conversationRepository;
		this.messageRepository = messageRepository;
		this.citationRepository = citationRepository;
		this.embeddingService = embeddingService;
		this.documentRepository = documentRepository;
		this.llmClient = llmClient;
		this.promptBuilder = promptBuilder;
		this.contextualQueryService = contextualQueryService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.objectMapper = objectMapper;
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
		PreparedTurn turn = prepareTurn(workspaceId, conversationId, request);
		String answer = llmClient.chat(turn.systemPrompt(), turn.llmMessages());
		return finalizeTurn(turn, answer);
	}

	/**
	 * Streams the assistant answer as SSE events:
	 * {@code user}, {@code token}, {@code done}, {@code error}.
	 * <p>
	 * Failures during prepare are returned as an SSE {@code error} event (not a JSON
	 * 400) so {@code Accept: text/event-stream} clients can consume them.
	 */
	public SseEmitter streamMessage(UUID workspaceId, UUID conversationId, SendMessageRequest request) {
		SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
		java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean(false);

		emitter.onTimeout(() -> completeQuietly(emitter, completed));
		emitter.onError(ex -> {
			log.debug("SSE connection closed: {}", ex.toString());
			completed.set(true);
		});
		emitter.onCompletion(() -> completed.set(true));

		PreparedTurn turn;
		try {
			turn = transactionTemplate.execute(status -> prepareTurn(workspaceId, conversationId, request));
			if (turn == null) {
				throw new BadRequestException("Failed to prepare chat turn");
			}
		}
		catch (Exception prepareEx) {
			log.warn("SSE prepare failed: {}", prepareEx.getMessage());
			streamExecutor.execute(() -> {
				try {
					sendEvent(emitter, "error", Map.of(
							"message",
							prepareEx.getMessage() == null ? "Failed to prepare chat" : prepareEx.getMessage()
					));
				}
				catch (Exception ignored) {
					// ignore
				}
				completeQuietly(emitter, completed);
			});
			return emitter;
		}

		// Capture auth for the worker thread (JWT context is thread-local by default).
		var securityContext = org.springframework.security.core.context.SecurityContextHolder.getContext();
		final PreparedTurn prepared = turn;

		streamExecutor.execute(() -> {
			org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);
			try {
				sendEvent(emitter, "user", Map.of(
						"conversationId", prepared.conversationId(),
						"userMessage", prepared.userMessageResponse(),
						"citations", prepared.citations(),
						"model", llmClient.modelId()
				));

				StringBuilder full = new StringBuilder();
				llmClient.streamChat(prepared.systemPrompt(), prepared.llmMessages(), delta -> {
					full.append(delta);
					try {
						sendEvent(emitter, "token", Map.of("delta", delta));
					}
					catch (IOException ex) {
						// Client disconnected mid-stream; stop generating further events.
						throw new IllegalStateException("SSE client disconnected", ex);
					}
				});

				ChatAnswerResponse done = transactionTemplate.execute(
						status -> finalizeTurn(prepared, full.toString())
				);
				sendEvent(emitter, "done", done);
				completeQuietly(emitter, completed);
			}
			catch (Exception ex) {
				log.error("Streaming chat failed", ex);
				try {
					if (!completed.get()) {
						sendEvent(emitter, "error", Map.of(
								"message", ex.getMessage() == null ? "Streaming failed" : ex.getMessage()
						));
					}
				}
				catch (Exception ignored) {
					// ignore secondary send failures
				}
				completeQuietly(emitter, completed);
			}
			finally {
				org.springframework.security.core.context.SecurityContextHolder.clearContext();
			}
		});

		return emitter;
	}

	private static void completeQuietly(SseEmitter emitter, java.util.concurrent.atomic.AtomicBoolean completed) {
		if (completed.compareAndSet(false, true)) {
			try {
				emitter.complete();
			}
			catch (Exception ignored) {
				// already completed/closed
			}
		}
	}

	@Transactional
	public void softDeleteConversation(UUID workspaceId, UUID conversationId) {
		Conversation conversation = requireOwnedConversation(workspaceId, conversationId);
		conversation.softDelete();
		conversationRepository.save(conversation);
	}

	private PreparedTurn prepareTurn(UUID workspaceId, UUID conversationId, SendMessageRequest request) {
		Conversation conversation = requireOwnedConversation(workspaceId, conversationId);

		String userText = request.message().trim();
		if (userText.isEmpty()) {
			throw new BadRequestException("message must not be blank");
		}

		int topK = request.topK() == null ? DEFAULT_TOP_K : request.topK();

		if ("New conversation".equals(conversation.getTitle())) {
			String title = userText.length() > 80 ? userText.substring(0, 80) + "..." : userText;
			conversation.setTitle(title);
		}

		// Prior turns only (before this message) — used for history-aware retrieval
		List<ChatMessage> priorMessages =
				messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);

		ChatMessage userMessage = messageRepository.save(
				new ChatMessage(conversation.getId(), MessageRole.USER, userText)
		);

		// Conversational retrieval: history-aware rewrite → multi-query + RRF when needed
		ContextualQueryResult queryPlan = contextualQueryService.prepare(userText, priorMessages);
		List<SearchHitResponse> hits = embeddingService.searchMulti(
				workspaceId,
				queryPlan.searchQueries(),
				topK
		);
		log.info(
				"RAG retrieve conversation={} method={} rewritten={} multi={} queryCount={} originalChars={} searchChars={} hits={}",
				conversationId,
				queryPlan.method(),
				queryPlan.rewritten(),
				queryPlan.multiQuery(),
				queryPlan.searchQueries().size(),
				queryPlan.originalQuery().length(),
				queryPlan.searchQuery().length(),
				hits.size()
		);
		log.info(
				"RAG retrieve queries original='{}' primary='{}' all={}",
				queryPlan.originalQuery(),
				queryPlan.searchQuery(),
				queryPlan.searchQueries()
		);

		List<String> filenames = resolveFilenames(hits);
		RagPromptBuilder.BuiltPrompt built = promptBuilder.build(
				userText,
				queryPlan.resolvedQuestion(),
				hits,
				filenames
		);

		List<ChatMessage> history = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
		List<LlmMessage> llmMessages = toLlmHistory(history, built.userPrompt());

		conversationRepository.save(conversation);

		ChatMessageResponse userMessageResponse = new ChatMessageResponse(
				userMessage.getId(),
				userMessage.getRole(),
				userMessage.getContent(),
				userMessage.getCreatedAt(),
				List.of()
		);

		return new PreparedTurn(
				conversation.getId(),
				userMessageResponse,
				built.systemPrompt(),
				llmMessages,
				built.citations()
		);
	}

	private ChatAnswerResponse finalizeTurn(PreparedTurn turn, String answer) {
		// Belt-and-suspenders: never persist Qwen-style think blocks
		String content = com.secondbrain.ai.llm.ThinkingStreamFilter.stripComplete(
				answer == null ? "" : answer
		);
		if (content.isEmpty()) {
			content = "I could not generate an answer. Please try again.";
		}

		ChatMessage assistantMessage = messageRepository.save(
				new ChatMessage(turn.conversationId(), MessageRole.ASSISTANT, content)
		);

		for (CitationResponse citation : turn.citations()) {
			citationRepository.save(new MessageCitation(
					assistantMessage.getId(),
					citation.chunkId(),
					citation.documentId(),
					citation.chunkIndex(),
					citation.score(),
					citation.sourceFilename(),
					citation.snippet()
			));
		}

		// Touch conversation updatedAt
		conversationRepository.findById(turn.conversationId()).ifPresent(conversationRepository::save);

		List<CitationResponse> citationResponses = turn.citations();
		return new ChatAnswerResponse(
				turn.conversationId(),
				turn.userMessageResponse(),
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

	private void sendEvent(SseEmitter emitter, String name, Object data) throws IOException {
		// Pass the object directly so Spring serializes once (avoid double-encoded JSON strings).
		emitter.send(SseEmitter.event()
				.name(name)
				.data(data, MediaType.APPLICATION_JSON));
	}

	private List<LlmMessage> toLlmHistory(List<ChatMessage> history, String currentUserPromptWithContext) {
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

	private record PreparedTurn(
			UUID conversationId,
			ChatMessageResponse userMessageResponse,
			String systemPrompt,
			List<LlmMessage> llmMessages,
			List<CitationResponse> citations
	) {
	}
}
