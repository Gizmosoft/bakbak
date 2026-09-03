package uk.deadcatlab.bakbak.dto.response;

import java.time.Instant;
import java.util.UUID;
import uk.deadcatlab.bakbak.dto.EncryptionType;
import uk.deadcatlab.bakbak.dto.MessageType;
import uk.deadcatlab.bakbak.model.OutboxMessage;

/**
 * REST payload for {@code GET /api/inbox/pending} — same envelope shape as WebSocket relay.
 */
public record PendingMessageResponse(
	UUID id,
	Long conversationId,
	Long senderId,
	String content,
	Instant sentAt,
	Instant serverReceivedAt,
	MessageType type,
	EncryptionType encryption,
	AttachmentSummary attachment
) {

	public static PendingMessageResponse fromOutbox(OutboxMessage row, AttachmentSummary attachment) {
		EncryptionType encryption = row.getEncryption() != null ? row.getEncryption() : EncryptionType.NONE;
		return new PendingMessageResponse(
			row.getMessageId(),
			row.getConversationId(),
			row.getSenderId(),
			row.getContent(),
			row.getCreatedAt(),
			row.getCreatedAt(),
			MessageType.CHAT,
			encryption,
			attachment
		);
	}

	public ChatMessageBroadcast toBroadcast() {
		return new ChatMessageBroadcast(
			id,
			conversationId,
			senderId,
			content,
			sentAt,
			serverReceivedAt,
			type,
			encryption,
			attachment
		);
	}
}
