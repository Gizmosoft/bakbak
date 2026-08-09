package uk.deadcatlab.bakbak.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published Signal identity public key for a user (single-device registration).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_identity_keys")
public class UserIdentityKey {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Column(name = "registration_id", nullable = false)
	private Integer registrationId;

	@Column(name = "identity_key_public", nullable = false, columnDefinition = "TEXT")
	private String identityKeyPublic;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "rotated_at", nullable = false)
	private Instant rotatedAt;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		if (createdAt == null) {
			createdAt = now;
		}
		if (rotatedAt == null) {
			rotatedAt = now;
		}
	}

	@PreUpdate
	void onUpdate() {
		rotatedAt = Instant.now();
	}
}
