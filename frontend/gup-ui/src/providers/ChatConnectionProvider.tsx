import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { AppState, type AppStateStatus } from 'react-native';

import { resyncConversationsFromServer, resyncPendingInbox } from '@/db/sync/bootstrap';
import { useAuth } from '@/providers/AuthProvider';
import { chatClient } from '@/websocket/chat.client';
import {
  applyDeliveryReceipt,
  confirmOutboundMessage,
  failOutboundMessage,
  flushDeliveryAcks,
  persistIncomingMessage,
  retryPendingOutbox,
} from '@/websocket/message-sync';

const PRESENCE_PING_MS = 30_000;

/** Keeps the STOMP connection alive while the user is authenticated. */
export function ChatConnectionProvider({ children }: { children: React.ReactNode }) {
  const { token, isAuthenticated, user } = useAuth();
  const queryClient = useQueryClient();
  const presencePingRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (!isAuthenticated || !token || !user) {
      chatClient.disconnect();
      return;
    }

    chatClient.connect(token);
  }, [isAuthenticated, token, user]);

  useEffect(() => {
    if (!user) {
      return;
    }

    const currentUserId = user.id;

    chatClient.setUserQueueHandlers({
      onInbox: (broadcast) => {
        void persistIncomingMessage(queryClient, broadcast, currentUserId);
      },
      onSent: (broadcast) => {
        void confirmOutboundMessage(queryClient, broadcast);
      },
      onDeliveryReceipt: (receipt) => {
        void applyDeliveryReceipt(queryClient, receipt);
      },
    });

    chatClient.setSendLifecycleHandlers({
      onSendTimeout: (clientId, conversationId) => {
        void failOutboundMessage(queryClient, clientId, conversationId);
      },
    });

    chatClient.onConnected(() => {
      // Safety net for connect-time inbox drain race: REST pending after user queues are subscribed.
      void (async () => {
        try {
          await resyncPendingInbox(currentUserId);
          flushDeliveryAcks();
        } catch (error) {
          if (__DEV__) {
            console.error('[ChatConnectionProvider] inbox resync on connect failed', error);
          }
        }
      })();
      void retryPendingOutbox(queryClient, currentUserId);

      if (presencePingRef.current) {
        clearInterval(presencePingRef.current);
      }
      chatClient.sendPresencePing();
      presencePingRef.current = setInterval(() => {
        chatClient.sendPresencePing();
      }, PRESENCE_PING_MS);
    });

    return () => {
      chatClient.setUserQueueHandlers({});
      chatClient.setSendLifecycleHandlers({});
      chatClient.onConnected(null);
      if (presencePingRef.current) {
        clearInterval(presencePingRef.current);
        presencePingRef.current = null;
      }
    };
  }, [queryClient, user]);

  useEffect(() => {
    if (!isAuthenticated) {
      if (presencePingRef.current) {
        clearInterval(presencePingRef.current);
        presencePingRef.current = null;
      }
    }
  }, [isAuthenticated]);

  useEffect(() => {
    if (!isAuthenticated || !token || !user) {
      return;
    }

    const currentUserId = user.id;

    const handleAppState = (state: AppStateStatus): void => {
      if (state === 'background' || state === 'inactive') {
        chatClient.pauseForBackground();
        if (presencePingRef.current) {
          clearInterval(presencePingRef.current);
          presencePingRef.current = null;
        }
        return;
      }

      if (state === 'active') {
        chatClient.connect(token);
        void (async () => {
          try {
            await resyncConversationsFromServer();
            await resyncPendingInbox(currentUserId);
            flushDeliveryAcks();
          } catch (error) {
            if (__DEV__) {
              console.error('[ChatConnectionProvider] foreground resync failed', error);
            }
          }
        })();
      }
    };

    const subscription = AppState.addEventListener('change', handleAppState);
    return () => subscription.remove();
  }, [isAuthenticated, token, user]);

  return children;
}
