package uk.deadcatlab.bakbak.dto.response;

import java.time.Instant;
import java.util.UUID;
import uk.deadcatlab.bakbak.dto.EncryptionType;
import uk.deadcatlab.bakbak.dto.MessageType;

/**
 * Canonical {@code MessageEnvelope} broadcast on {@code /topic/conversation/{conversationId}},
 * {@code /user/queue/inbox}, and {@code /user/queue/sent}.
 *
 * <p>{@code id} is client-generated (UUID v4) before send; the server sets
 * {@code serverReceivedAt} on ingest. {@code encryption} marks whether {@code content} is
 * plaintext or opaque Signal ciphertext.</p>
 */
public record ChatMessageBroadcast(
	UUID id,
	Long conversationId,
	Long senderId,
	String content,
	Instant sentAt,
	Instant serverReceivedAt,
	MessageType type,
	EncryptionType encryption
) {
	public ChatMessageBroadcast {
		if (encryption == null) {
			encryption = EncryptionType.NONE;
		}
	}
}
