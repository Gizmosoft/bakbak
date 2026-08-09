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
 * Published signed prekey (X25519 public + Ed25519 signature over the public key).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "signed_pre_keys")
@IdClass(SignedPreKey.SignedPreKeyId.class)
public class SignedPreKey {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Id
	@Column(name = "key_id")
	private Integer keyId;

	@Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
	private String publicKey;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String signature;

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
	public static class SignedPreKeyId implements Serializable {
		private Long userId;
		private Integer keyId;
	}
}
