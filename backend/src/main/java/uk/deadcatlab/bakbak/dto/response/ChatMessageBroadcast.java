package uk.deadcatlab.bakbak.dto.response;

import java.time.Instant;

/**
 * Payload broadcast to {@code /topic/conversation/{conversationId}} after a message is sent.
 *
 * <p>Includes {@code senderUsername} for client display; REST history uses {@link MessageResponse}
 * without username.</p>
 */
public record ChatMessageBroadcast(
	Long id,
	Long conversationId,
	Long senderId,
	String senderUsername,
	String content,
	Instant createdAt
) {}
