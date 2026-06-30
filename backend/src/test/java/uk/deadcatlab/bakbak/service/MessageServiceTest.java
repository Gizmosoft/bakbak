package uk.deadcatlab.bakbak.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import uk.deadcatlab.bakbak.dto.MessageType;
import uk.deadcatlab.bakbak.dto.response.ChatMessageBroadcast;
import uk.deadcatlab.bakbak.model.OutboxMessage;
import uk.deadcatlab.bakbak.model.User;
import uk.deadcatlab.bakbak.repository.ConversationParticipantRepository;
import uk.deadcatlab.bakbak.repository.ConversationRepository;
import uk.deadcatlab.bakbak.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

	private static final Long CONVERSATION_ID = 10L;
	private static final Long SENDER_ID = 1L;
	private static final Long RECIPIENT_ID = 2L;

	@Mock ConversationRepository conversationRepository;
	@Mock ConversationParticipantRepository participantRepository;
	@Mock UserRepository userRepository;
	@Mock OutboxService outboxService;
	@Mock PresenceService presenceService;
	@Mock SimpMessagingTemplate messagingTemplate;

	MessageService messageService;

	@BeforeEach
	void setUp() {
		messageService = new MessageService(
			conversationRepository,
			participantRepository,
			userRepository,
			outboxService,
			presenceService,
			messagingTemplate
		);
	}

	@Test
	void send_relaysToTopicAndEnqueuesWhenRecipientOffline() {
		UUID messageId = UUID.randomUUID();
		User sender = new User();
		sender.setId(SENDER_ID);
		sender.setUsername("alice");

		when(conversationRepository.existsById(CONVERSATION_ID)).thenReturn(true);
		when(userRepository.findById(SENDER_ID)).thenReturn(Optional.of(sender));
		when(participantRepository.findUserIdsByConversationId(CONVERSATION_ID))
			.thenReturn(List.of(SENDER_ID, RECIPIENT_ID));
		when(presenceService.isOnline(RECIPIENT_ID)).thenReturn(false);

		messageService.send(CONVERSATION_ID, SENDER_ID, messageId, "hello");

		verify(messagingTemplate).convertAndSend(eq("/topic/conversation/10"), any(ChatMessageBroadcast.class));
		verify(outboxService).enqueue(any(OutboxMessage.class));
		verify(messagingTemplate).convertAndSendToUser(eq("alice"), eq("/queue/sent"), any(ChatMessageBroadcast.class));
	}

	@Test
	void send_skipsOutboxWhenRecipientOnline() {
		User sender = new User();
		sender.setId(SENDER_ID);
		sender.setUsername("alice");

		when(conversationRepository.existsById(CONVERSATION_ID)).thenReturn(true);
		when(userRepository.findById(SENDER_ID)).thenReturn(Optional.of(sender));
		when(participantRepository.findUserIdsByConversationId(CONVERSATION_ID))
			.thenReturn(List.of(SENDER_ID, RECIPIENT_ID));
		when(presenceService.isOnline(RECIPIENT_ID)).thenReturn(true);

		messageService.send(CONVERSATION_ID, SENDER_ID, null, "hello");

		verify(messagingTemplate).convertAndSend(eq("/topic/conversation/10"), any(ChatMessageBroadcast.class));
		verify(outboxService, never()).enqueue(any(OutboxMessage.class));
	}

	@Test
	void acknowledgeDelivery_notifiesSenderWithDeliveredEnvelope() {
		UUID messageId = UUID.randomUUID();
		User sender = new User();
		sender.setId(SENDER_ID);
		sender.setUsername("alice");

		when(outboxService.acknowledge(messageId, RECIPIENT_ID, CONVERSATION_ID))
			.thenReturn(Optional.of(SENDER_ID));
		when(userRepository.findById(SENDER_ID)).thenReturn(Optional.of(sender));

		messageService.acknowledgeDelivery(
			messageId,
			CONVERSATION_ID,
			RECIPIENT_ID,
			java.time.Instant.parse("2026-04-19T12:00:00Z")
		);

		verify(messagingTemplate).convertAndSendToUser(
			eq("alice"),
			eq("/queue/delivery-receipts"),
			org.mockito.ArgumentMatchers.argThat((ChatMessageBroadcast receipt) ->
				receipt.type() == MessageType.DELIVERED && receipt.id().equals(messageId))
		);
	}

	@Test
	void acknowledgeDelivery_wrongRecipientThrows() {
		assertThatThrownBy(() ->
			messageService.acknowledgeDeliveryAsUser(
				UUID.randomUUID(),
				CONVERSATION_ID,
				RECIPIENT_ID,
				99L,
				java.time.Instant.parse("2026-04-19T12:00:00Z")
			)
		).isInstanceOf(uk.deadcatlab.bakbak.exception.ForbiddenException.class);
	}
}
