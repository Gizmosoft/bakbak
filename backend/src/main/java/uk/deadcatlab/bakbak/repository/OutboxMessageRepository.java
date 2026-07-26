package uk.deadcatlab.bakbak.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.deadcatlab.bakbak.model.OutboxMessage;

/**
 * Persistence operations for the temporary server outbox.
 */
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

	List<OutboxMessage> findAllByRecipientIdOrderByCreatedAtAsc(Long recipientId);

	boolean existsByMessageId(UUID messageId);

	boolean existsByMessageIdAndRecipientId(UUID messageId, Long recipientId);

	Optional<OutboxMessage> findByMessageIdAndRecipientId(UUID messageId, Long recipientId);

	/**
	 * Idempotent delete used by delivery ACK. Returns rows removed (0 if already ACKed /
	 * concurrent delete won the race).
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		delete from OutboxMessage o
		where o.messageId = :messageId
		  and o.recipientId = :recipientId
		  and o.conversationId = :conversationId
		""")
	int deleteByMessageIdAndRecipientIdAndConversationId(
		@Param("messageId") UUID messageId,
		@Param("recipientId") Long recipientId,
		@Param("conversationId") Long conversationId
	);

	void deleteByExpiresAtBefore(Instant expiresAt);
}
