import { listConversations } from '@/api/conversations.api';
import { listPendingInbox } from '@/api/inbox.api';
import * as conversationRepository from '@/db/repositories/conversation.repository';
import * as messageRepository from '@/db/repositories/message.repository';
import { syncConversationsFromServer } from '@/db/sync/conversation-sync';
import { queueDeliveryAck } from '@/websocket/message-sync';
import type { ChatMessageBroadcast } from '@/types/message';
import { envelopeToMessage } from '@/types/message';

function pendingToBroadcast(pending: {
  id: string;
  conversationId: number;
  senderId: number;
  content: string;
  sentAt: string;
  serverReceivedAt: string | null;
  type: ChatMessageBroadcast['type'];
}): ChatMessageBroadcast {
  return {
    id: pending.id,
    conversationId: pending.conversationId,
    senderId: pending.senderId,
    content: pending.content,
    sentAt: pending.sentAt,
    serverReceivedAt: pending.serverReceivedAt,
    type: pending.type,
  };
}

/** Seeds SQLite from the server after login or session restore. */
export async function bootstrapLocalStore(recipientUserId: number): Promise<void> {
  const conversations = await listConversations();
  await syncConversationsFromServer(conversations);

  const pending = await listPendingInbox();
  const ackedAt = new Date().toISOString();

  for (const item of pending) {
    const broadcast = pendingToBroadcast(item);
    await messageRepository.insertMessage(envelopeToMessage(broadcast, 'SENT'));
    await conversationRepository.updateLastMessage(
      String(broadcast.conversationId),
      broadcast.content,
      broadcast.sentAt
    );
    queueDeliveryAck({
      messageId: broadcast.id,
      conversationId: broadcast.conversationId,
      recipientId: recipientUserId,
      ackedAt,
    });
  }
}
