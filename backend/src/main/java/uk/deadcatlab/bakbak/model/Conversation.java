package uk.deadcatlab.bakbak.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Conversation thread stored in {@code conversations}.
 *
 * <p>{@code participantKey} is a unique, deterministic key (e.g. {@code "minUserId:maxUserId"}) used to
 * prevent duplicate 1:1 conversations under concurrent creation.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "conversations")
public class Conversation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "participant_key", nullable = false, unique = true, length = 50)
	private String participantKey;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "last_message_at")
	private Instant lastMessageAt;

	@PrePersist
    @SuppressWarnings("unused")
	void onCreate() {
		// Mirror DB default for created_at.
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}

