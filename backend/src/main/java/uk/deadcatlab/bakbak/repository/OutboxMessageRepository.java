package uk.deadcatlab.bakbak.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.deadcatlab.bakbak.model.OutboxMessage;

/**
 * Persistence operations for the temporary server outbox.
 */
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

	List<OutboxMessage> findAllByRecipientIdOrderByCreatedAtAsc(Long recipientId);

	boolean existsByMessageId(UUID messageId);

	boolean existsByMessageIdAndRecipientId(UUID messageId, Long recipientId);

	Optional<OutboxMessage> findByMessageIdAndRecipientId(UUID messageId, Long recipientId);

	void deleteByMessageIdAndRecipientId(UUID messageId, Long recipientId);

	void deleteByExpiresAtBefore(Instant expiresAt);
}
