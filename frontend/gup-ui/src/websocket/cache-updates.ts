import type { QueryClient } from '@tanstack/react-query';

import * as conversationRepository from '@/db/repositories/conversation.repository';
import * as messageRepository from '@/db/repositories/message.repository';
import { queryKeys } from '@/constants/query-keys';
import type { ChatMessageBroadcast } from '@/types/message';
import { envelopeToMessage } from '@/types/message';

const MESSAGE_PAGE_SIZE = 50;

export async function persistIncomingMessage(
  queryClient: QueryClient,
  broadcast: ChatMessageBroadcast
): Promise<void> {
  await messageRepository.insertMessage(envelopeToMessage(broadcast, 'SENT'));
  await conversationRepository.updateLastMessage(
    String(broadcast.conversationId),
    broadcast.content,
    broadcast.sentAt
  );

  await queryClient.invalidateQueries({ queryKey: queryKeys.messages(broadcast.conversationId) });
  await queryClient.invalidateQueries({ queryKey: queryKeys.conversations });
}

export async function invalidateMessageQueries(
  queryClient: QueryClient,
  conversationId: number
): Promise<void> {
  await queryClient.invalidateQueries({ queryKey: queryKeys.messages(conversationId) });
  await queryClient.invalidateQueries({ queryKey: queryKeys.conversations });
}

export { MESSAGE_PAGE_SIZE };
