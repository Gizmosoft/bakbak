/** Single message row from GET /api/conversations/{id}/messages. */
export type MessageResponse = {
  id: number;
  conversationId: number;
  senderId: number;
  content: string;
  /** ISO-8601 timestamp, e.g. 2026-04-19T14:32:00Z */
  createdAt: string;
};

/** Inbound STOMP payload for SEND /app/chat/{conversationId}. */
export type SendMessageRequest = {
  content: string;
};

/** Payload broadcast to /topic/conversation/{conversationId} after a message is sent. */
export type ChatMessageBroadcast = {
  id: number;
  conversationId: number;
  senderId: number;
  senderUsername: string;
  content: string;
  /** ISO-8601 timestamp, e.g. 2026-04-19T18:30:00Z */
  createdAt: string;
};
