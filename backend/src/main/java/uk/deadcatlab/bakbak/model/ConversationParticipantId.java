package uk.deadcatlab.bakbak.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Composite primary key for {@link ConversationParticipant}.
 *
 * <p>The underlying table uses {@code PRIMARY KEY (conversation_id, user_id)} to prevent duplicates
 * and make membership lookups efficient.</p>
 *
 * <p>Setters are required: Hibernate uses them (with {@code @MapsId}) to copy FK values from
 * {@link ConversationParticipant#getConversation()} / {@link ConversationParticipant#getUser()}
 * into this embeddable after identifiers are assigned.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class ConversationParticipantId implements Serializable {

	@Column(name = "conversation_id", nullable = false)
	private Long conversationId;

	@Column(name = "user_id", nullable = false)
	private Long userId;
}

