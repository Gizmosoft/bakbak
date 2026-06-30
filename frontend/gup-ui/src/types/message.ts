/** Discriminator for relayed WebSocket payloads and SQLite message rows. */
export type MessageType = 'CHAT' | 'ACK' | 'DELIVERED' | 'SYSTEM';

export type MessageStatus = 'SENDING' | 'SENT' | 'DELIVERED' | 'FAILED';

/**
 * Canonical message envelope shared across WebSocket send, relay broadcast, and SQLite insert.
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

/** Row shape stored in SQLite {@code messages}. */
export type Message = {
  id: string;
  conversationId: number;
  senderId: number;
  content: string;
  sentAt: string;
  serverReceivedAt: string | null;
  status: MessageStatus;
  /** Client-generated UUID before send; matches envelope id for outbound messages. */
  clientId: string;
};

/** UI-facing message row (SQLite-backed). */
export type MessageResponse = {
  id: string;
  conversationId: number;
  senderId: number;
  content: string;
  /** ISO-8601 timestamp aligned with {@link Message.sentAt}. */
  sentAt: string;
  serverReceivedAt: string | null;
  status: MessageStatus;
  clientId: string;
};

/** Inbound STOMP payload for SEND /app/chat/{conversationId}. */
export type SendMessageRequest = {
  id?: string;
  content: string;
};

/** Client-side send queue row in SQLite {@code outbox_pending}. */
export type OutboxPending = {
  id: string;
  conversationId: string;
  content: string;
  createdAt: string;
  retryCount: number;
};

export function messageToResponse(message: Message): MessageResponse {
  return {
    id: message.id,
    conversationId: message.conversationId,
    senderId: message.senderId,
    content: message.content,
    sentAt: message.sentAt,
    serverReceivedAt: message.serverReceivedAt,
    status: message.status,
    clientId: message.clientId,
  };
}

export function envelopeToMessage(
  envelope: MessageEnvelope,
  status: MessageStatus = 'SENT'
): Message {
  return {
    id: envelope.id,
    clientId: envelope.id,
    conversationId: envelope.conversationId,
    senderId: envelope.senderId,
    content: envelope.content,
    sentAt: envelope.sentAt,
    serverReceivedAt: envelope.serverReceivedAt,
    status,
  };
}
