import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';

import { useAuth } from '@/providers/AuthProvider';
import { persistIncomingMessage } from '@/websocket/message-sync';
import { chatClient } from '@/websocket/chat.client';

/**
 * Subscribes to conversation topic plus user-specific relay queues while a chat is open.
 */
export function useChatSubscription(conversationId: number): void {
  const queryClient = useQueryClient();
  const { user } = useAuth();

  useEffect(() => {
    if (!Number.isFinite(conversationId) || conversationId <= 0 || !user) {
      return;
    }

    return chatClient.subscribeToConversation(conversationId, (broadcast) => {
      void persistIncomingMessage(queryClient, broadcast, user.id);
    });
  }, [conversationId, queryClient, user]);
}
