package uk.deadcatlab.bakbak.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.deadcatlab.bakbak.model.Conversation;

/**
 * Persistence operations for {@link Conversation}.
 */
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
	/**
	 * Finds the unique 1:1 conversation identified by {@code participantKey}.
	 *
	 * <p>This supports idempotent "get or create" behavior when two users start chatting.</p>
	 */
	Optional<Conversation> findByParticipantKey(String participantKey);
}

