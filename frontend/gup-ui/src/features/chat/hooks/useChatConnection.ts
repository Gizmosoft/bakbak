import { useEffect, useState } from 'react';

import { chatClient } from '@/websocket/chat.client';

export function useChatConnection(): { isConnected: boolean; statusMessage: string | null } {
  const [isConnected, setIsConnected] = useState(chatClient.isConnected());
  const [statusMessage, setStatusMessage] = useState(chatClient.getStatusMessage());

  useEffect(() => {
    const unsubConnect = chatClient.onConnectionChange(setIsConnected);
    const unsubStatus = chatClient.onStatusChange(setStatusMessage);
    return () => {
      unsubConnect();
      unsubStatus();
    };
  }, []);

  return { isConnected, statusMessage };
}
