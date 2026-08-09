package uk.deadcatlab.bakbak.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import uk.deadcatlab.bakbak.dto.EncryptionType;

/**
 * Inbound STOMP payload for {@code SEND /app/chat/{conversationId}}.
 *
 * <p>{@code id} is an optional client-generated UUID v4 for idempotent send; the server assigns one
 * when omitted. {@code encryption} defaults to {@link EncryptionType#NONE} when absent.</p>
 */
public record SendMessageRequest(
	UUID id,
	@NotBlank
	@Size(min = 1, max = 16384)
	String content,
	EncryptionType encryption
) {
	public SendMessageRequest {
		if (encryption == null) {
			encryption = EncryptionType.NONE;
		}
	}
}
