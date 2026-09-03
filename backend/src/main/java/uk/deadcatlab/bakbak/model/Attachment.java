package uk.deadcatlab.bakbak.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.deadcatlab.bakbak.dto.AttachmentStatus;

/**
 * Metadata for a media object in S3-compatible storage.
 *
 * <p>Only {@code object_key} is persisted; presigned URLs are minted on demand and never stored.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "attachments")
public class Attachment {

	@Id
	private UUID id;

	@Column(name = "message_id")
	private UUID messageId;

	@Column(name = "uploader_id", nullable = false)
	private Long uploaderId;

	@Column(name = "conversation_id", nullable = false)
	private Long conversationId;

	@Column(name = "object_key", nullable = false, length = 512)
	private String objectKey;

	@Column(name = "mime_type", nullable = false, length = 128)
	private String mimeType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private AttachmentStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

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
