import { useEffect, useState } from 'react';

import { chatClient } from '@/websocket/chat.client';

export function useChatConnection(): { isConnected: boolean } {
  const [isConnected, setIsConnected] = useState(chatClient.isConnected());

  useEffect(() => chatClient.onConnectionChange(setIsConnected), []);

  return { isConnected };
}
