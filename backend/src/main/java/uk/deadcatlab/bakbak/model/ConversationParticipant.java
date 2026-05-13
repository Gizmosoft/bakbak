package uk.deadcatlab.bakbak.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Join entity for conversation membership stored in {@code conversation_participants}.
 *
 * <p>This is modeled as a composite key ({@link ConversationParticipantId}) to match the database schema and
 * guarantee one membership row per (conversation, user).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "conversation_participants")
public class ConversationParticipant {

	// Hibernate 7's @MapsId generator copies FK ids INTO this embeddable; it does not
	// instantiate it for us, so we must provide a non-null instance up front.
	@EmbeddedId
	private ConversationParticipantId id = new ConversationParticipantId();

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId("conversationId")
	@JoinColumn(name = "conversation_id", nullable = false)
	private Conversation conversation;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId("userId")
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;

	@PrePersist
    @SuppressWarnings("unused")
	void onCreate() {
		// Mirror DB default for joined_at.
		if (joinedAt == null) {
			joinedAt = Instant.now();
		}
		// Convenience: if the association is set, derive the composite key automatically.
		if (id == null && conversation != null && user != null) {
			id = new ConversationParticipantId(conversation.getId(), user.getId());
		}
	}
}

