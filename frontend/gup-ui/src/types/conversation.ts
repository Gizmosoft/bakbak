import type { UserPublicResponse } from './user';

/** Preview of the most recent message in a conversation list row. */
export type LastMessagePreview = {
  content: string;
  senderId: number;
};

/** Response from POST /api/conversations and rows of GET /api/conversations. */
export type ConversationResponse = {
  conversationId: number;
  otherUser: UserPublicResponse;
  lastMessage: LastMessagePreview | null;
  /** ISO-8601 timestamp, e.g. 2026-04-19T14:32:00Z */
  lastMessageAt: string | null;
};

/** Request body for POST /api/conversations. */
export type CreateConversationRequest = {
  targetUserId: number;
};
