import type { InfiniteData, QueryClient } from '@tanstack/react-query';

import { queryKeys } from '@/constants/query-keys';
import type { ConversationResponse } from '@/types/conversation';
import type { ChatMessageBroadcast, MessageResponse } from '@/types/message';

const MESSAGE_PAGE_SIZE = 50;

function broadcastToMessage(broadcast: ChatMessageBroadcast): MessageResponse {
  return {
    id: broadcast.id,
    conversationId: broadcast.conversationId,
    senderId: broadcast.senderId,
    content: broadcast.content,
    createdAt: broadcast.createdAt,
  };
}

export function appendMessageToCache(
  queryClient: QueryClient,
  conversationId: number,
  broadcast: ChatMessageBroadcast
): void {
  const message = broadcastToMessage(broadcast);

  queryClient.setQueryData<InfiniteData<MessageResponse[], string | undefined>>(
    queryKeys.messages(conversationId),
    (current) => {
      if (!current) {
        return current;
      }

      const alreadyExists = current.pages.some((page) =>
        page.some((item) => item.id === message.id)
      );
      if (alreadyExists) {
        return current;
      }

      const pages = [...current.pages];
      const lastIndex = pages.length - 1;
      pages[lastIndex] = [...pages[lastIndex], message];

      return { ...current, pages };
    }
  );
}

export function updateConversationPreview(
  queryClient: QueryClient,
  broadcast: ChatMessageBroadcast
): void {
  queryClient.setQueryData<ConversationResponse[]>(queryKeys.conversations, (current) => {
    if (!current) {
      return current;
    }

    const index = current.findIndex(
      (conversation) => conversation.conversationId === broadcast.conversationId
    );
    if (index === -1) {
      return current;
    }

    const updated: ConversationResponse = {
      ...current[index],
      lastMessage: {
        content: broadcast.content,
        senderId: broadcast.senderId,
      },
      lastMessageAt: broadcast.createdAt,
    };

    return [updated, ...current.filter((_, itemIndex) => itemIndex !== index)];
  });
}

export function mergeIncomingMessage(
  queryClient: QueryClient,
  broadcast: ChatMessageBroadcast
): void {
  appendMessageToCache(queryClient, broadcast.conversationId, broadcast);
  updateConversationPreview(queryClient, broadcast);
}

export { MESSAGE_PAGE_SIZE };
