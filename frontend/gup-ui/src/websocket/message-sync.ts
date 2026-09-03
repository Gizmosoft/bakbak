import * as Crypto from 'expo-crypto';
import type { QueryClient } from '@tanstack/react-query';

import {
  createAttachmentIntent,
  uploadToPresignedUrl,
} from '@/api/attachments.api';
import * as conversationRepository from '@/db/repositories/conversation.repository';
import * as messageRepository from '@/db/repositories/message.repository';
import * as outboxRepository from '@/db/repositories/outbox.repository';
import { queryKeys } from '@/constants/query-keys';
import {
  decryptFromPeer,
  encryptForPeer,
} from '@/crypto';
import type {
  AttachmentSummary,
  ChatMessageBroadcast,
  DeliveryAck,
  Message,
  MessageResponse,
} from '@/types/message';
import { envelopeToMessage } from '@/types/message';
import { chatClient } from '@/websocket/chat.client';

const MESSAGE_PAGE_SIZE = 50;
const MAX_SEND_RETRIES = 5;

export type OutboundAttachment = {
  uri: string;
  mimeType: string;
  sizeBytes: number;
  fileName: string;
};

const pendingAcks: DeliveryAck[] = [];
/** In-session dedupe so topic + inbox + reconnect resync do not ACK the same message twice. */
const ackedMessageIds = new Set<string>();

export function queueDeliveryAck(ack: DeliveryAck): void {
  if (ackedMessageIds.has(ack.messageId)) {
    return;
  }
  if (pendingAcks.some((pending) => pending.messageId === ack.messageId)) {
    return;
  }
  pendingAcks.push(ack);
}

export function flushDeliveryAcks(): void {
  for (const ack of pendingAcks.splice(0)) {
    if (ackedMessageIds.has(ack.messageId)) {
      continue;
    }
    ackedMessageIds.add(ack.messageId);
    chatClient.sendDeliveryAck(ack);
  }
}

function sendDeliveryAckOnce(ack: DeliveryAck): void {
  if (ackedMessageIds.has(ack.messageId)) {
    return;
  }
  ackedMessageIds.add(ack.messageId);
  chatClient.sendDeliveryAck(ack);
}

export async function invalidateMessageQueries(
  queryClient: QueryClient,
  conversationId: number
): Promise<void> {
  await queryClient.invalidateQueries({ queryKey: queryKeys.messages(conversationId) });
  await queryClient.invalidateQueries({ queryKey: queryKeys.conversations });
}

async function resolvePeerUserId(
  conversationId: number,
  currentUserId: number,
  otherUserId?: number
): Promise<number> {
  if (otherUserId != null && otherUserId !== currentUserId) {
    return otherUserId;
  }
  const conversation = await conversationRepository.getConversation(String(conversationId));
  if (!conversation) {
    throw new Error(`Conversation ${conversationId} not found locally`);
  }
  return Number(conversation.otherUserId);
}

async function encryptOutboundContent(
  conversationId: number,
  senderId: number,
  plaintext: string
): Promise<string> {
  const peerUserId = await resolvePeerUserId(conversationId, senderId);
  return encryptForPeer(peerUserId, plaintext);
}

async function decryptInboundContent(
  broadcast: ChatMessageBroadcast,
  currentUserId: number
): Promise<string> {
  const encryption = broadcast.encryption ?? 'NONE';
  if (encryption !== 'SIGNAL_V1') {
    return broadcast.content;
  }
  if (broadcast.senderId === currentUserId) {
    // Own echo already stored as plaintext locally; ignore ciphertext body.
    return broadcast.content;
  }
  return decryptFromPeer(broadcast.senderId, broadcast.content);
}

export async function prepareOutboundMessage(
  queryClient: QueryClient,
  conversationId: number,
  senderId: number,
  content: string,
  clientId: string,
  attachment?: AttachmentSummary | null
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
    encryption: 'NONE',
    attachment: attachment ?? null,
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
  if (isOwnMessage) {
    // Sender already has plaintext locally; sent echo confirmation is handled separately.
    return;
  }

  let plaintext: string;
  try {
    plaintext = await decryptInboundContent(broadcast, currentUserId);
  } catch (error) {
    console.warn('Failed to decrypt inbound message', broadcast.id, error);
    plaintext = '[Unable to decrypt message]';
  }

  const localEnvelope = {
    ...broadcast,
    content: plaintext,
    encryption: 'NONE' as const,
    attachment: broadcast.attachment ?? null,
  };

  await messageRepository.insertMessage(envelopeToMessage(localEnvelope, 'SENT'));
  await conversationRepository.updateLastMessage(
    String(broadcast.conversationId),
    previewForMessage(plaintext, broadcast.attachment),
    broadcast.sentAt
  );
  await invalidateMessageQueries(queryClient, broadcast.conversationId);

  sendDeliveryAckOnce({
    messageId: broadcast.id,
    conversationId: broadcast.conversationId,
    recipientId: currentUserId,
    ackedAt: new Date().toISOString(),
  });
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

  const ciphertext = await encryptOutboundContent(
    message.conversationId,
    senderId,
    message.content
  );
  await chatClient.sendMessage(
    message.conversationId,
    senderId,
    ciphertext,
    message.clientId,
    'SIGNAL_V1'
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
      const ciphertext = await encryptOutboundContent(
        Number(item.conversationId),
        senderId,
        item.content
      );
      await chatClient.sendMessage(
        Number(item.conversationId),
        senderId,
        ciphertext,
        item.id,
        'SIGNAL_V1'
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
  content: string,
  outboundAttachment?: OutboundAttachment
): Promise<void> {
  const clientId = Crypto.randomUUID();
  let attachmentSummary: AttachmentSummary | null = null;

  if (outboundAttachment) {
    const intent = await createAttachmentIntent({
      conversationId,
      mimeType: outboundAttachment.mimeType,
      sizeBytes: outboundAttachment.sizeBytes,
      fileName: outboundAttachment.fileName,
    });
    await uploadToPresignedUrl(
      intent.uploadUrl,
      outboundAttachment.uri,
      outboundAttachment.mimeType
    );
    attachmentSummary = {
      id: intent.attachmentId,
      mimeType: outboundAttachment.mimeType,
      sizeBytes: outboundAttachment.sizeBytes,
    };
  }

  await prepareOutboundMessage(
    queryClient,
    conversationId,
    senderId,
    content,
    clientId,
    attachmentSummary
  );

  if (!chatClient.isConnected()) {
    throw new Error('Chat is not connected');
  }

  try {
    const ciphertext = content
      ? await encryptOutboundContent(conversationId, senderId, content)
      : '';
    await chatClient.sendMessage(
      conversationId,
      senderId,
      ciphertext,
      clientId,
      content ? 'SIGNAL_V1' : 'NONE',
      attachmentSummary?.id
    );
  } catch (error) {
    await failOutboundMessage(queryClient, clientId, conversationId);
    throw error;
  }
}

function previewForMessage(content: string, attachment?: AttachmentSummary | null): string {
  if (content.trim()) {
    return content;
  }
  if (!attachment) {
    return '';
  }
  if (attachment.mimeType.startsWith('image/')) {
    return '📷 Photo';
  }
  if (attachment.mimeType.startsWith('video/')) {
    return '🎬 Video';
  }
  if (attachment.mimeType.startsWith('audio/')) {
    return '🎵 Audio';
  }
  return '📎 Attachment';
}

export { MESSAGE_PAGE_SIZE, MAX_SEND_RETRIES };
