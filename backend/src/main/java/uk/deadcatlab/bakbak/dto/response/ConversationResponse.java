package uk.deadcatlab.bakbak.dto.response;

import java.time.Instant;

/**
 * Conversation entry returned by the conversation endpoints.
 *
 * <p>Used both for {@code POST /api/conversations} (create-or-fetch) and as the row shape
 * of {@code GET /api/conversations} (contact window).</p>
 *
 * <p>{@code lastMessage} and {@code lastMessageAt} are {@code null} for freshly created
 * conversations that have no messages yet.</p>
 */
public record ConversationResponse(
	Long conversationId,
	UserPublicResponse otherUser,
	LastMessagePreview lastMessage,
	Instant lastMessageAt
) {

	/**
	 * Preview of the most recent message in a conversation.
	 *
	 * <p>Kept minimal so the contact window can render without fetching full message history.</p>
	 */
	public record LastMessagePreview(
		String content,
		Long senderId
	) {}
}
