package uk.deadcatlab.bakbak.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.deadcatlab.bakbak.model.OutboxMessage;
import uk.deadcatlab.bakbak.repository.OutboxMessageRepository;

/**
 * Temporary server outbox for offline recipients.
 *
 * <p>Rows are inserted when a recipient is offline, drained on WebSocket connect, and deleted after
 * delivery ACK.</p>
 */
@Service
public class OutboxService {

	private final OutboxMessageRepository outboxRepository;
	private final int ttlDays;

	public OutboxService(
		OutboxMessageRepository outboxRepository,
		@Value("${bakbak.outbox.ttl-days:30}") int ttlDays
	) {
		this.outboxRepository = outboxRepository;
		this.ttlDays = ttlDays;
	}

	/**
	 * Idempotent insert: skips when the same {@code messageId} already exists for the recipient
	 * (client retry) or globally (duplicate fan-out guard).
	 */
	@Transactional
	public void enqueue(OutboxMessage message) {
		if (outboxRepository.existsByMessageIdAndRecipientId(message.getMessageId(), message.getRecipientId())
			|| outboxRepository.existsByMessageId(message.getMessageId())) {
			return;
		}
		if (message.getExpiresAt() == null) {
			Instant createdAt = message.getCreatedAt() != null ? message.getCreatedAt() : Instant.now();
			message.setExpiresAt(createdAt.plus(ttlDays, ChronoUnit.DAYS));
		}
		outboxRepository.save(message);
	}

	@Transactional(readOnly = true)
	public List<OutboxMessage> listPendingForRecipient(Long recipientId) {
		return outboxRepository.findAllByRecipientIdOrderByCreatedAtAsc(recipientId);
	}

	@Transactional(readOnly = true)
	public List<OutboxMessage> drainForRecipient(Long recipientId) {
		return listPendingForRecipient(recipientId);
	}

	/**
	 * Idempotent ACK: deletes the matching outbox row via bulk delete so concurrent ACKs
	 * (topic + inbox, reconnect resync) do not throw {@code ObjectOptimisticLockingFailureException}.
	 *
	 * @return the original sender id when this call removed the row, or empty if already gone
	 */
	@Transactional
	public java.util.Optional<Long> acknowledge(UUID messageId, Long recipientId, Long conversationId) {
		java.util.Optional<OutboxMessage> existing =
			outboxRepository.findByMessageIdAndRecipientId(messageId, recipientId)
				.filter(row -> row.getConversationId().equals(conversationId));
		if (existing.isEmpty()) {
			return java.util.Optional.empty();
		}

		Long senderId = existing.get().getSenderId();
		int deleted = outboxRepository.deleteByMessageIdAndRecipientIdAndConversationId(
			messageId,
			recipientId,
			conversationId
		);
		if (deleted == 0) {
			// Another concurrent ACK already removed the row.
			return java.util.Optional.empty();
		}
		return java.util.Optional.of(senderId);
	}

	@Scheduled(fixedRate = 3_600_000)
	@Transactional
	public void pruneExpired() {
		outboxRepository.deleteByExpiresAtBefore(Instant.now());
	}
}
