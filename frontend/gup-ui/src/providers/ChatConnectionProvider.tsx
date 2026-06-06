import { useEffect } from 'react';

import { useAuth } from '@/providers/AuthProvider';
import { chatClient } from '@/websocket/chat.client';

/** Keeps the STOMP connection alive while the user is authenticated. */
export function ChatConnectionProvider({ children }: { children: React.ReactNode }) {
  const { token, isAuthenticated } = useAuth();

  useEffect(() => {
    if (isAuthenticated && token) {
      chatClient.connect(token);
    } else {
      chatClient.disconnect();
    }
  }, [isAuthenticated, token]);

  useEffect(() => {
    return () => {
      chatClient.disconnect();
    };
  }, []);

  return children;
}
