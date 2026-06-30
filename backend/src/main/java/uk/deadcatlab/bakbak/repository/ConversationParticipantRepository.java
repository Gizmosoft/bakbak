package uk.deadcatlab.bakbak.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.deadcatlab.bakbak.model.ConversationParticipant;
import uk.deadcatlab.bakbak.model.ConversationParticipantId;

/**
 * Persistence operations for {@link ConversationParticipant} membership rows.
 */
public interface ConversationParticipantRepository
	extends JpaRepository<ConversationParticipant, ConversationParticipantId> {

	/**
	 * Batched lookup of the "other" participant for a list of conversations.
	 *
	 * <p>Used by the contact-window query to attach each conversation to its non-self participant
	 * in a single round-trip rather than N+1 lookups.</p>
	 */
	@Query("""
			select cp.conversation.id as conversationId,
				   cp.user.id as userId,
				   cp.user.username as username,
				   cp.user.displayName as displayName
			from ConversationParticipant cp
			where cp.conversation.id in :conversationIds
			  and cp.user.id <> :excludeUserId
			""")
	List<OtherParticipantView> findOtherParticipantsByConversationIds(
		@Param("conversationIds") Collection<Long> conversationIds,
		@Param("excludeUserId") Long excludeUserId
	);

	@Query("""
			select cp.user.id
			from ConversationParticipant cp
			where cp.conversation.id = :conversationId
			""")
	List<Long> findUserIdsByConversationId(@Param("conversationId") Long conversationId);

	/**
	 * Projection of a conversation's "other participant" — i.e. the user who is not the requesting user.
	 *
	 * <p>Returns just enough fields to populate {@code UserPublicResponse} without loading the full entity.</p>
	 */
	interface OtherParticipantView {
		Long getConversationId();
		Long getUserId();
		String getUsername();
		String getDisplayName();
	}
}
