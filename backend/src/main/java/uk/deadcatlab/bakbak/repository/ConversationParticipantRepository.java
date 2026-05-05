package uk.deadcatlab.bakbak.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.deadcatlab.bakbak.model.ConversationParticipant;
import uk.deadcatlab.bakbak.model.ConversationParticipantId;

/**
 * Persistence operations for {@link ConversationParticipant} membership rows.
 */
public interface ConversationParticipantRepository
	extends JpaRepository<ConversationParticipant, ConversationParticipantId> {
}

