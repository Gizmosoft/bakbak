import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';

import { persistIncomingMessage } from '@/websocket/cache-updates';
import { chatClient } from '@/websocket/chat.client';

export function useChatSubscription(conversationId: number): void {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!Number.isFinite(conversationId) || conversationId <= 0) {
      return;
    }

    return chatClient.subscribeToConversation(conversationId, (broadcast) => {
      void persistIncomingMessage(queryClient, broadcast);
    });
  }, [conversationId, queryClient]);
}
