package uk.deadcatlab.bakbak.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.deadcatlab.bakbak.dto.MessageType;
import uk.deadcatlab.bakbak.dto.response.ChatMessageBroadcast;
import uk.deadcatlab.bakbak.dto.response.MessageResponse;
import uk.deadcatlab.bakbak.exception.ResourceNotFoundException;
import uk.deadcatlab.bakbak.model.Conversation;
import uk.deadcatlab.bakbak.model.Message;
import uk.deadcatlab.bakbak.model.User;
import uk.deadcatlab.bakbak.repository.ConversationRepository;
import uk.deadcatlab.bakbak.repository.MessageRepository;
import uk.deadcatlab.bakbak.repository.UserRepository;

/**
 * Message persistence use-cases: send (WebSocket) and history pagination (REST).
 */
@Service
public class MessageService {

	private static final int MIN_PAGE_SIZE = 1;
	private static final int MAX_PAGE_SIZE = 100;

	private final MessageRepository messageRepository;
	private final ConversationRepository conversationRepository;
	private final UserRepository userRepository;
	private final SimpMessagingTemplate messagingTemplate;

	public MessageService(
		MessageRepository messageRepository,
		ConversationRepository conversationRepository,
		UserRepository userRepository,
		SimpMessagingTemplate messagingTemplate
	) {
		this.messageRepository = messageRepository;
		this.conversationRepository = conversationRepository;
		this.userRepository = userRepository;
		this.messagingTemplate = messagingTemplate;
	}

	/**
	 * Persists a message, updates {@code conversations.last_message_at}, and broadcasts to
	 * {@code /topic/conversation/{conversationId}}.
	 *
	 * <p>Transactional: one message insert + one conversation update. Authorization (participant
	 * check) is enforced by callers before this method.</p>
	 */
	@Transactional
	public MessageResponse send(Long conversationId, Long senderId, String content) {
		Conversation conversation = conversationRepository.findById(conversationId)
			.orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
		User sender = userRepository.findById(senderId)
			.orElseThrow(() -> new ResourceNotFoundException("User not found"));

		Message message = new Message();
		message.setConversation(conversation);
		message.setSender(sender);
		message.setContent(content);

		Message saved = messageRepository.save(message);
		Instant createdAt = saved.getCreatedAt();

		conversation.setLastMessageAt(createdAt);
		conversationRepository.save(conversation);

		MessageResponse response = toMessageResponse(saved);
		// Server-assigned envelope id until clients send client-generated UUIDs (Phase 3).
		ChatMessageBroadcast broadcast = new ChatMessageBroadcast(
			UUID.randomUUID(),
			response.conversationId(),
			response.senderId(),
			response.content(),
			response.createdAt(),
			response.createdAt(),
			MessageType.CHAT
		);
		messagingTemplate.convertAndSend(topicDestination(conversationId), broadcast);
		return response;
	}

	/**
	 * Returns up to {@code limit} messages for {@code conversationId}, newest-first within the
	 * selected window, then reordered to chronological ascending (oldest of the batch first).
	 *
	 * <p>Cursor semantics match {@code GET /api/conversations/{id}/messages}:</p>
	 * <ul>
	 *   <li>{@code before == null} — the {@code limit} newest messages in the conversation.</li>
	 *   <li>{@code before != null} — the {@code limit} newest messages strictly older than
	 *       {@code before} ({@code created_at < before}), for stable infinite scroll.</li>
	 * </ul>
	 *
	 * <p>{@code limit} is clamped to 1..100; callers may also enforce defaults at the HTTP layer.</p>
	 *
	 * <p>Authorization (participant check) is enforced by callers — typically the REST controller
	 * invokes {@link ConversationService#assertParticipant(Long, Long)} before this method.</p>
	 */
	@Transactional(readOnly = true)
	public List<MessageResponse> getHistory(Long conversationId, Instant before, int limit) {
		int effectiveLimit = Math.clamp(limit, MIN_PAGE_SIZE, MAX_PAGE_SIZE);
		PageRequest page = PageRequest.of(0, effectiveLimit);
		List<MessageResponse> newestFirst = before == null
			? messageRepository.findNewestPage(conversationId, page)
			: messageRepository.findHistoryPageBefore(conversationId, before, page);
		if (newestFirst.isEmpty()) {
			return List.of();
		}
		ArrayList<MessageResponse> chronological = new ArrayList<>(newestFirst);
		Collections.reverse(chronological);
		return chronological;
	}

	private static String topicDestination(Long conversationId) {
		return "/topic/conversation/" + conversationId;
	}

	private static MessageResponse toMessageResponse(Message message) {
		return new MessageResponse(
			message.getId(),
			message.getConversation().getId(),
			message.getSender().getId(),
			message.getContent(),
			message.getCreatedAt()
		);
	}
}
