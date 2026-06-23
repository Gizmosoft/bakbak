/** Discriminator for relayed WebSocket payloads and SQLite message rows. */
export type MessageType = 'CHAT' | 'ACK' | 'DELIVERED' | 'SYSTEM';

/**
 * Canonical message envelope shared across WebSocket send, relay broadcast, and SQLite insert.
 *
 * {@code id} is client-generated (UUID v4) before send; the server sets {@code serverReceivedAt}
 * on ingest.
 */
export type MessageEnvelope = {
  id: string;
  conversationId: number;
  senderId: number;
  content: string;
  /** ISO-8601 client timestamp, e.g. 2026-04-19T14:32:00Z */
  sentAt: string;
  /** ISO-8601 server timestamp; null until the server receives the message */
  serverReceivedAt: string | null;
  type: MessageType;
};

/** Payload broadcast to /topic/conversation/{conversationId} and user-specific relay queues. */
export type ChatMessageBroadcast = MessageEnvelope;

/** STOMP payload for SEND /app/ack after a message is persisted on device. */
export type DeliveryAck = {
  messageId: string;
  conversationId: number;
  recipientId: number;
  /** ISO-8601 timestamp when the recipient stored the message */
  ackedAt: string;
};

/** Single message row from GET /api/conversations/{id}/messages (legacy REST history). */
export type MessageResponse = {
  /** Numeric server id from REST; UUID string from real-time {@link MessageEnvelope} until SQLite cutover. */
  id: number | string;
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
