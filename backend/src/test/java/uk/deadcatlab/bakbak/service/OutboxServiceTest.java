package uk.deadcatlab.bakbak.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.deadcatlab.bakbak.model.OutboxMessage;
import uk.deadcatlab.bakbak.repository.OutboxMessageRepository;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

	@Mock OutboxMessageRepository outboxRepository;

	OutboxService outboxService;

	@BeforeEach
	void setUp() {
		outboxService = new OutboxService(outboxRepository, 30);
	}

	@Test
	void enqueue_skipsDuplicateMessageForRecipient() {
		UUID messageId = UUID.randomUUID();
		OutboxMessage row = OutboxMessage.builder()
			.messageId(messageId)
			.recipientId(2L)
			.build();
		when(outboxRepository.existsByMessageIdAndRecipientId(messageId, 2L)).thenReturn(true);

		outboxService.enqueue(row);

		verify(outboxRepository, never()).save(any());
	}

	@Test
	void enqueue_setsExpiryAndPersists() {
		UUID messageId = UUID.randomUUID();
		Instant createdAt = Instant.parse("2026-04-19T12:00:00Z");
		OutboxMessage row = OutboxMessage.builder()
			.messageId(messageId)
			.recipientId(2L)
			.createdAt(createdAt)
			.build();
		when(outboxRepository.existsByMessageIdAndRecipientId(messageId, 2L)).thenReturn(false);
		when(outboxRepository.existsByMessageId(messageId)).thenReturn(false);

		outboxService.enqueue(row);

		ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
		verify(outboxRepository).save(captor.capture());
		assertThat(captor.getValue().getExpiresAt()).isEqualTo(createdAt.plus(30, ChronoUnit.DAYS));
	}

	@Test
	void drainForRecipient_returnsRowsInOrder() {
		OutboxMessage first = OutboxMessage.builder().recipientId(5L).build();
		OutboxMessage second = OutboxMessage.builder().recipientId(5L).build();
		when(outboxRepository.findAllByRecipientIdOrderByCreatedAtAsc(5L)).thenReturn(List.of(first, second));

		assertThat(outboxService.drainForRecipient(5L)).containsExactly(first, second);
	}

	@Test
	void enqueue_skipsWhenMessageIdExistsGlobally() {
		UUID messageId = UUID.randomUUID();
		OutboxMessage row = OutboxMessage.builder()
			.messageId(messageId)
			.recipientId(2L)
			.build();
		when(outboxRepository.existsByMessageIdAndRecipientId(messageId, 2L)).thenReturn(false);
		when(outboxRepository.existsByMessageId(messageId)).thenReturn(true);

		outboxService.enqueue(row);

		verify(outboxRepository, never()).save(any());
	}

	@Test
	void acknowledge_deletesMatchingRowAndReturnsSender() {
		UUID messageId = UUID.randomUUID();
		OutboxMessage row = OutboxMessage.builder()
			.messageId(messageId)
			.conversationId(7L)
			.recipientId(3L)
			.senderId(9L)
			.build();
		when(outboxRepository.findByMessageIdAndRecipientId(messageId, 3L)).thenReturn(Optional.of(row));
		when(outboxRepository.deleteByMessageIdAndRecipientIdAndConversationId(messageId, 3L, 7L))
			.thenReturn(1);

		assertThat(outboxService.acknowledge(messageId, 3L, 7L)).contains(9L);
		verify(outboxRepository).deleteByMessageIdAndRecipientIdAndConversationId(messageId, 3L, 7L);
	}

	@Test
	void acknowledge_wrongConversationReturnsEmpty() {
		UUID messageId = UUID.randomUUID();
		OutboxMessage row = OutboxMessage.builder()
			.messageId(messageId)
			.conversationId(7L)
			.recipientId(3L)
			.senderId(9L)
			.build();
		when(outboxRepository.findByMessageIdAndRecipientId(messageId, 3L)).thenReturn(Optional.of(row));

		assertThat(outboxService.acknowledge(messageId, 3L, 99L)).isEmpty();
		verify(outboxRepository, never())
			.deleteByMessageIdAndRecipientIdAndConversationId(any(), any(), any());
	}

	@Test
	void acknowledge_missingRowReturnsEmpty() {
		UUID messageId = UUID.randomUUID();
		when(outboxRepository.findByMessageIdAndRecipientId(messageId, 3L)).thenReturn(Optional.empty());

		assertThat(outboxService.acknowledge(messageId, 3L, 7L)).isEmpty();
		verify(outboxRepository, never())
			.deleteByMessageIdAndRecipientIdAndConversationId(any(), any(), any());
	}

	@Test
	void acknowledge_alreadyDeletedByConcurrentAckReturnsEmpty() {
		UUID messageId = UUID.randomUUID();
		OutboxMessage row = OutboxMessage.builder()
			.messageId(messageId)
			.conversationId(7L)
			.recipientId(3L)
			.senderId(9L)
			.build();
		when(outboxRepository.findByMessageIdAndRecipientId(messageId, 3L)).thenReturn(Optional.of(row));
		when(outboxRepository.deleteByMessageIdAndRecipientIdAndConversationId(messageId, 3L, 7L))
			.thenReturn(0);

		assertThat(outboxService.acknowledge(messageId, 3L, 7L)).isEmpty();
	}

	@Test
	void pruneExpired_deletesExpiredRows() {
		outboxService.pruneExpired();
		verify(outboxRepository).deleteByExpiresAtBefore(any(Instant.class));
	}
}
