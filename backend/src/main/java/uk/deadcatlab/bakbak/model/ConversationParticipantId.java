package uk.deadcatlab.bakbak.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Composite primary key for {@link ConversationParticipant}.
 *
 * <p>The underlying table uses {@code PRIMARY KEY (conversation_id, user_id)} to prevent duplicates
 * and make membership lookups efficient.</p>
 */
@Getter
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

