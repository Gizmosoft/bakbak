package uk.deadcatlab.bakbak.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.deadcatlab.bakbak.dto.MessageType;
import uk.deadcatlab.bakbak.dto.response.ChatMessageBroadcast;
import uk.deadcatlab.bakbak.exception.ForbiddenException;
import uk.deadcatlab.bakbak.exception.ResourceNotFoundException;
import uk.deadcatlab.bakbak.model.OutboxMessage;
import uk.deadcatlab.bakbak.model.User;
import uk.deadcatlab.bakbak.repository.ConversationParticipantRepository;
import uk.deadcatlab.bakbak.repository.ConversationRepository;
import uk.deadcatlab.bakbak.repository.UserRepository;

/**
 * Store-and-forward message relay.
 *
 * <p>Every recipient gets an outbox row until delivery ACK (durable path). Online recipients also
 * receive an immediate push on {@code /user/queue/inbox} (always subscribed while connected). The
 * conversation topic remains a fast path for an open chat screen — presence alone must not imply
 * topic subscription.</p>
 *
 * <p>Participant authorization is enforced by callers ({@link uk.deadcatlab.bakbak.websocket.WebSocketAuthorizationInterceptor}
 * or REST controllers) before methods here run.</p>
 */
@Service
public class MessageService {

	private static final String SENT_QUEUE = "/queue/sent";
	private static final String INBOX_QUEUE = "/queue/inbox";
	private static final String DELIVERY_RECEIPTS_QUEUE = "/queue/delivery-receipts";

	private final ConversationRepository conversationRepository;
	private final ConversationParticipantRepository participantRepository;
	private final UserRepository userRepository;
	private final OutboxService outboxService;
	private final PresenceService presenceService;
	private final SimpMessagingTemplate messagingTemplate;

	public MessageService(
		ConversationRepository conversationRepository,
		ConversationParticipantRepository participantRepository,
		UserRepository userRepository,
		OutboxService outboxService,
		PresenceService presenceService,
		SimpMessagingTemplate messagingTemplate
	) {
		this.conversationRepository = conversationRepository;
		this.participantRepository = participantRepository;
		this.userRepository = userRepository;
		this.outboxService = outboxService;
		this.presenceService = presenceService;
		this.messagingTemplate = messagingTemplate;
	}

	/**
	 * Relays a chat message: topic broadcast, durable outbox for each recipient, inbox push when
	 * online, and sender echo on {@code /user/queue/sent}.
	 */
	@Transactional
	public ChatMessageBroadcast send(
		Long conversationId,
		Long senderId,
		UUID messageId,
		String content
	) {
		if (!conversationRepository.existsById(conversationId)) {
			throw new ResourceNotFoundException("Conversation not found");
		}
		User sender = userRepository.findById(senderId)
			.orElseThrow(() -> new ResourceNotFoundException("User not found"));

		UUID envelopeId = messageId != null ? messageId : UUID.randomUUID();
		Instant now = Instant.now();
		ChatMessageBroadcast envelope = new ChatMessageBroadcast(
			envelopeId,
			conversationId,
			senderId,
			content,
			now,
			now,
			MessageType.CHAT
		);

		// Fast path for clients currently viewing this conversation.
		messagingTemplate.convertAndSend(topicDestination(conversationId), envelope);

		List<Long> participants = participantRepository.findUserIdsByConversationId(conversationId);
		for (Long recipientId : participants) {
			if (recipientId.equals(senderId)) {
				continue;
			}

			// Durable until ACK — covers "online but not subscribed to this topic".
			outboxService.enqueue(OutboxMessage.builder()
				.conversationId(conversationId)
				.senderId(senderId)
				.recipientId(recipientId)
				.messageId(envelopeId)
				.content(content)
				.createdAt(now)
				.build());

			if (presenceService.isOnline(recipientId)) {
				userRepository.findById(recipientId).ifPresent(recipient ->
					messagingTemplate.convertAndSendToUser(recipient.getUsername(), INBOX_QUEUE, envelope)
				);
			}
		}

		messagingTemplate.convertAndSendToUser(sender.getUsername(), SENT_QUEUE, envelope);
		return envelope;
	}

	/**
	 * Deletes the outbox row after delivery ACK and optionally notifies the original sender.
	 */
	@Transactional
	public void acknowledgeDelivery(UUID messageId, Long conversationId, Long recipientId, Instant ackedAt) {
		java.util.Optional<Long> senderId = outboxService.acknowledge(messageId, recipientId, conversationId);
		if (senderId.isEmpty()) {
			return;
		}

		User sender = userRepository.findById(senderId.get()).orElse(null);
		if (sender == null) {
			return;
		}

		ChatMessageBroadcast receipt = new ChatMessageBroadcast(
			messageId,
			conversationId,
			recipientId,
			"",
			ackedAt,
			Instant.now(),
			MessageType.DELIVERED
		);
		messagingTemplate.convertAndSendToUser(sender.getUsername(), DELIVERY_RECEIPTS_QUEUE, receipt);
	}

	/**
	 * Verifies the ACK-ing user matches {@code recipientId} before delegating to
	 * {@link #acknowledgeDelivery}.
	 */
	@Transactional
	public void acknowledgeDeliveryAsUser(
		UUID messageId,
		Long conversationId,
		Long recipientId,
		Long authenticatedUserId,
		Instant ackedAt
	) {
		if (!recipientId.equals(authenticatedUserId)) {
			throw new ForbiddenException("ACK recipient does not match authenticated user");
		}
		acknowledgeDelivery(messageId, conversationId, recipientId, ackedAt);
	}

	/**
	 * Pushes pending outbox rows to {@code /user/queue/inbox} after WebSocket connect.
	 */
	@Transactional(readOnly = true)
	public void pushPendingInbox(Long recipientId, String username) {
		for (OutboxMessage row : outboxService.drainForRecipient(recipientId)) {
			ChatMessageBroadcast envelope = new ChatMessageBroadcast(
				row.getMessageId(),
				row.getConversationId(),
				row.getSenderId(),
				row.getContent(),
				row.getCreatedAt(),
				row.getCreatedAt(),
				MessageType.CHAT
			);
			messagingTemplate.convertAndSendToUser(username, INBOX_QUEUE, envelope);
		}
	}

	private static String topicDestination(Long conversationId) {
		return "/topic/conversation/" + conversationId;
	}
}
