import * as Crypto from 'expo-crypto';
import type { QueryClient } from '@tanstack/react-query';

import * as conversationRepository from '@/db/repositories/conversation.repository';
import * as messageRepository from '@/db/repositories/message.repository';
import * as outboxRepository from '@/db/repositories/outbox.repository';
import { queryKeys } from '@/constants/query-keys';
import type {
  ChatMessageBroadcast,
  DeliveryAck,
  Message,
  MessageResponse,
  MessageStatus,
} from '@/types/message';
import { envelopeToMessage } from '@/types/message';
import { chatClient } from '@/websocket/chat.client';

const MESSAGE_PAGE_SIZE = 50;
const MAX_SEND_RETRIES = 5;

const pendingAcks: DeliveryAck[] = [];

export function queueDeliveryAck(ack: DeliveryAck): void {
  pendingAcks.push(ack);
}

export function flushDeliveryAcks(): void {
  for (const ack of pendingAcks.splice(0)) {
    chatClient.sendDeliveryAck(ack);
  }
}

export async function invalidateMessageQueries(
  queryClient: QueryClient,
  conversationId: number
): Promise<void> {
  await queryClient.invalidateQueries({ queryKey: queryKeys.messages(conversationId) });
  await queryClient.invalidateQueries({ queryKey: queryKeys.conversations });
}

export async function prepareOutboundMessage(
  queryClient: QueryClient,
  conversationId: number,
  senderId: number,
  content: string,
  clientId: string
): Promise<Message> {
  const sentAt = new Date().toISOString();
  const message: Message = {
    id: clientId,
    clientId,
    conversationId,
    senderId,
    content,
    sentAt,
    serverReceivedAt: null,
    status: 'SENDING',
  };

  await outboxRepository.enqueue({
    id: clientId,
    conversationId: String(conversationId),
    content,
    createdAt: sentAt,
    retryCount: 0,
  });
  await messageRepository.insertMessage(message);
  await invalidateMessageQueries(queryClient, conversationId);
  return message;
}

export async function confirmOutboundMessage(
  queryClient: QueryClient,
  broadcast: ChatMessageBroadcast
): Promise<void> {
  await messageRepository.updateMessageStatus(broadcast.id, 'SENT');
  await outboxRepository.dequeue(broadcast.id);
  await invalidateMessageQueries(queryClient, broadcast.conversationId);
}

export async function failOutboundMessage(
  queryClient: QueryClient,
  clientId: string,
  conversationId: number
): Promise<void> {
  await messageRepository.updateMessageStatus(clientId, 'FAILED');
  await outboxRepository.incrementRetry(clientId);
  await invalidateMessageQueries(queryClient, conversationId);
}

export async function applyDeliveryReceipt(
  queryClient: QueryClient,
  receipt: ChatMessageBroadcast
): Promise<void> {
  if (receipt.type !== 'DELIVERED') {
    return;
  }
  await messageRepository.updateMessageStatus(receipt.id, 'DELIVERED');
  await invalidateMessageQueries(queryClient, receipt.conversationId);
}

export async function persistIncomingMessage(
  queryClient: QueryClient,
  broadcast: ChatMessageBroadcast,
  currentUserId: number
): Promise<void> {
  if (broadcast.type !== 'CHAT') {
    return;
  }

  const isOwnMessage = broadcast.senderId === currentUserId;
  await messageRepository.insertMessage(envelopeToMessage(broadcast, isOwnMessage ? 'SENT' : 'SENT'));
  await conversationRepository.updateLastMessage(
    String(broadcast.conversationId),
    broadcast.content,
    broadcast.sentAt
  );
  await invalidateMessageQueries(queryClient, broadcast.conversationId);

  if (!isOwnMessage) {
    chatClient.sendDeliveryAck({
      messageId: broadcast.id,
      conversationId: broadcast.conversationId,
      recipientId: currentUserId,
      ackedAt: new Date().toISOString(),
    });
  }
}

export async function retryFailedMessage(
  queryClient: QueryClient,
  message: MessageResponse,
  senderId: number
): Promise<void> {
  const sentAt = new Date().toISOString();
  await outboxRepository.enqueue({
    id: message.clientId,
    conversationId: String(message.conversationId),
    content: message.content,
    createdAt: sentAt,
    retryCount: 0,
  });
  await messageRepository.updateMessageStatus(message.id, 'SENDING');
  await invalidateMessageQueries(queryClient, message.conversationId);

  if (!chatClient.isConnected()) {
    throw new Error('Chat is not connected');
  }

  await chatClient.sendMessage(
    message.conversationId,
    senderId,
    message.content,
    message.clientId
  );
}

export async function retryPendingOutbox(
  queryClient: QueryClient,
  senderId: number
): Promise<void> {
  const pending = await outboxRepository.getPending();

  for (const item of pending) {
    if (item.retryCount >= MAX_SEND_RETRIES) {
      await outboxRepository.purgeFailed(MAX_SEND_RETRIES);
      await messageRepository.updateMessageStatus(item.id, 'FAILED');
      await invalidateMessageQueries(queryClient, Number(item.conversationId));
      continue;
    }

    try {
      await chatClient.sendMessage(
        Number(item.conversationId),
        senderId,
        item.content,
        item.id
      );
    } catch {
      await outboxRepository.incrementRetry(item.id);
      await messageRepository.updateMessageStatus(item.id, 'FAILED');
      await invalidateMessageQueries(queryClient, Number(item.conversationId));
    }
  }
}

export async function sendOutboundMessage(
  queryClient: QueryClient,
  conversationId: number,
  senderId: number,
  content: string
): Promise<void> {
  const clientId = Crypto.randomUUID();
  await prepareOutboundMessage(queryClient, conversationId, senderId, content, clientId);

  if (!chatClient.isConnected()) {
    throw new Error('Chat is not connected');
  }

  await chatClient.sendMessage(conversationId, senderId, content, clientId);
}

export { MESSAGE_PAGE_SIZE, MAX_SEND_RETRIES };
