package uk.deadcatlab.bakbak.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

	/**
	 * Returns conversations the given user participates in, sorted by creation time descending.
	 *
	 * <p>Message previews and {@code last_message_at} are maintained on-device in SQLite.</p>
	 */
	@Query("""
			select c from Conversation c
			where c.id in (
				select cp.conversation.id from ConversationParticipant cp where cp.user.id = :userId
			)
			order by c.createdAt desc
			""")
	List<Conversation> findAllForUser(@Param("userId") Long userId);
}
