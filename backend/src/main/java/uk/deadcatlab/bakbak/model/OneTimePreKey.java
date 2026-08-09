package uk.deadcatlab.bakbak.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One-time prekey public material. Consumed atomically when a bundle is fetched.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "one_time_pre_keys")
@IdClass(OneTimePreKey.OneTimePreKeyId.class)
public class OneTimePreKey {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Id
	@Column(name = "key_id")
	private Integer keyId;

	@Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
	private String publicKey;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class OneTimePreKeyId implements Serializable {
		private Long userId;
		private Integer keyId;
	}
}
