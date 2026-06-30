package uk.deadcatlab.bakbak.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Temporary server-side row for offline message delivery.
 *
 * <p>Deleted after the recipient ACKs via {@code SEND /app/ack}. Client-generated {@code messageId}
 * provides idempotent enqueue per recipient.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "outbox")
public class OutboxMessage {

	@Id
	private UUID id;

	@Column(name = "conversation_id", nullable = false)
	private Long conversationId;

	@Column(name = "sender_id", nullable = false)
	private Long senderId;

	@Column(name = "recipient_id", nullable = false)
	private Long recipientId;

	@Column(name = "message_id", nullable = false)
	private UUID messageId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
