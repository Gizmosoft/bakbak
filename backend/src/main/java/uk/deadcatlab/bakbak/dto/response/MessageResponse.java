package uk.deadcatlab.bakbak.dto.response;

import java.time.Instant;

/**
 * Single message row returned by {@code GET /api/conversations/{id}/messages}.
 *
 * <p>Matches the REST contract: id, conversation, sender, body, and creation time for cursor-based
 * history pagination.</p>
 */
public record MessageResponse(
	Long id,
	Long conversationId,
	Long senderId,
	String content,
	Instant createdAt
) {}
