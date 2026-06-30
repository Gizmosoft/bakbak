import type { PresenceStatus, UserPublicResponse } from './user';

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
  /** Derived on-device from SQLite when not provided by the server. */
  lastMessagePreview?: string | null;
  otherUserPresence?: PresenceStatus | 'UNKNOWN';
};

/** Request body for POST /api/conversations. */
export type CreateConversationRequest = {
  targetUserId: number;
};

/** Row stored in SQLite {@code conversations}. */
export type ConversationRecord = {
  id: string;
  participantKey: string | null;
  otherUserId: string;
  otherUserDisplayName: string | null;
  otherUserUsername: string;
  createdAt: string | null;
  lastMessageAt: string | null;
  lastMessagePreview: string | null;
};

export type ConversationWithLastMessage = ConversationRecord & {
  otherUserPresence: PresenceStatus | 'UNKNOWN';
};

export function conversationRecordToResponse(
  record: ConversationWithLastMessage
): ConversationResponse {
  return {
    conversationId: Number(record.id),
    otherUser: {
      id: Number(record.otherUserId),
      username: record.otherUserUsername,
      displayName: record.otherUserDisplayName,
    },
    lastMessage: record.lastMessagePreview
      ? {
          content: record.lastMessagePreview,
          senderId: Number(record.otherUserId),
        }
      : null,
    lastMessageAt: record.lastMessageAt,
    lastMessagePreview: record.lastMessagePreview,
    otherUserPresence: record.otherUserPresence,
  };
}

export function conversationResponseToRecord(
  response: ConversationResponse,
  participantKey: string | null = null
): ConversationRecord {
  return {
    id: String(response.conversationId),
    participantKey,
    otherUserId: String(response.otherUser.id),
    otherUserDisplayName: response.otherUser.displayName,
    otherUserUsername: response.otherUser.username,
    createdAt: response.lastMessageAt,
    lastMessageAt: response.lastMessageAt,
    lastMessagePreview: response.lastMessagePreview ?? response.lastMessage?.content ?? null,
  };
}
