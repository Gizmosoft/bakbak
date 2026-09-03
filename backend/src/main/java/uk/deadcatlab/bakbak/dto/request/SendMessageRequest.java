package uk.deadcatlab.bakbak.dto.request;

import jakarta.validation.constraints.Size;
import java.util.UUID;
import uk.deadcatlab.bakbak.dto.EncryptionType;

/**
 * Inbound STOMP payload for {@code SEND /app/chat/{conversationId}}.
 *
 * <p>{@code id} is an optional client-generated UUID v4 for idempotent send; the server assigns one
 * when omitted. {@code encryption} defaults to {@link EncryptionType#NONE} when absent. At least one
 * of {@code content} or {@code attachmentId} must be present.</p>
 */
public record SendMessageRequest(
	UUID id,
	@Size(max = 16384)
	String content,
	EncryptionType encryption,
	UUID attachmentId
) {
	public SendMessageRequest {
		if (encryption == null) {
			encryption = EncryptionType.NONE;
		}
		if (content == null) {
			content = "";
		}
		boolean hasContent = !content.isBlank();
		boolean hasAttachment = attachmentId != null;
		if (!hasContent && !hasAttachment) {
			throw new IllegalArgumentException("Message must have content or an attachment");
		}
	}
}
