package uk.deadcatlab.bakbak.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.deadcatlab.bakbak.dto.PresenceStatus;

/**
 * Latest known online/offline state for a user.
 *
 * <p>{@code sessionId} ties disconnect events to the active STOMP session when multiple tabs or
 * reconnect races occur.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_presence")
public class UserPresence {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private PresenceStatus status;

	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt;

	@Column(name = "session_id", length = 128)
	private String sessionId;

	@PrePersist
	void onCreate() {
		if (lastSeenAt == null) {
			lastSeenAt = Instant.now();
		}
	}

	@PreUpdate
	void onUpdate() {
		lastSeenAt = Instant.now();
	}
}
