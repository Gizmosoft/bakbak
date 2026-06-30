package uk.deadcatlab.bakbak.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Inbound STOMP payload for {@code SEND /app/chat/{conversationId}}.
 *
 * <p>{@code id} is an optional client-generated UUID v4 for idempotent send; the server assigns one
 * when omitted.</p>
 */
public record SendMessageRequest(
	UUID id,
	@NotBlank
	@Size(min = 1, max = 4000)
	String content
) {}
