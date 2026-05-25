package uk.deadcatlab.bakbak.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound STOMP payload for {@code SEND /app/chat/{conversationId}}.
 *
 * <p>Clients publish to the application destination; the server validates and persists in
 * {@link uk.deadcatlab.bakbak.service.MessageService#send} (Step 30).</p>
 */
public record SendMessageRequest(
	@NotBlank
	@Size(min = 1, max = 4000)
	String content
) {}
