import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import { AppState, type AppStateStatus } from 'react-native';

import { getWebSocketNativeUrl } from '@/config/env';
import type { ChatMessageBroadcast } from '@/types/message';

export type ChatMessageHandler = (message: ChatMessageBroadcast) => void;
export type ChatErrorHandler = (message: string) => void;

type ConnectionListener = (connected: boolean) => void;

class ChatWebSocketClient {
  private client: Client | null = null;
  private token: string | null = null;
  private topicSubscriptions = new Map<number, StompSubscription>();
  private messageHandlers = new Map<number, Set<ChatMessageHandler>>();
  private errorSubscription: StompSubscription | null = null;
  private errorHandler: ChatErrorHandler | null = null;
  private appStateSubscription: { remove: () => void } | null = null;
  private connectionListeners = new Set<ConnectionListener>();

  /** Subscribe to connect/disconnect changes (for React hooks). */
  onConnectionChange(listener: ConnectionListener): () => void {
    this.connectionListeners.add(listener);
    listener(this.isConnected());
    return () => {
      this.connectionListeners.delete(listener);
    };
  }

  private notifyConnectionChange(): void {
    const connected = this.isConnected();
    this.connectionListeners.forEach((listener) => listener(connected));
  }

  setErrorHandler(handler: ChatErrorHandler | null): void {
    this.errorHandler = handler;
  }

  connect(token: string): void {
    if (this.client?.active && this.token === token) {
      return;
    }

    this.disconnect();
    this.token = token;

    this.client = new Client({
      brokerURL: getWebSocketNativeUrl(),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 3_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      webSocketFactory: () => new WebSocket(getWebSocketNativeUrl()),
      onConnect: () => {
        this.resubscribeAll();
        this.notifyConnectionChange();
      },
      onDisconnect: () => {
        this.notifyConnectionChange();
      },
      onWebSocketClose: () => {
        this.notifyConnectionChange();
      },
      onStompError: (frame) => {
        this.errorHandler?.(frame.headers.message ?? 'Chat connection error');
        this.notifyConnectionChange();
      },
    });

    this.client.activate();
    this.setupAppStateListener();
  }

  disconnect(): void {
    this.appStateSubscription?.remove();
    this.appStateSubscription = null;

    this.topicSubscriptions.forEach((subscription) => subscription.unsubscribe());
    this.topicSubscriptions.clear();
    this.errorSubscription?.unsubscribe();
    this.errorSubscription = null;

    if (this.client) {
      void this.client.deactivate();
      this.client = null;
    }

    this.token = null;
    this.notifyConnectionChange();
  }

  isConnected(): boolean {
    return this.client?.connected ?? false;
  }

  subscribeToConversation(conversationId: number, handler: ChatMessageHandler): () => void {
    const handlers = this.messageHandlers.get(conversationId) ?? new Set<ChatMessageHandler>();
    handlers.add(handler);
    this.messageHandlers.set(conversationId, handlers);

    if (this.client?.connected) {
      this.ensureTopicSubscription(conversationId);
    }

    return () => {
      const currentHandlers = this.messageHandlers.get(conversationId);
      currentHandlers?.delete(handler);
      if (!currentHandlers || currentHandlers.size === 0) {
        this.messageHandlers.delete(conversationId);
        this.topicSubscriptions.get(conversationId)?.unsubscribe();
        this.topicSubscriptions.delete(conversationId);
      }
    };
  }

  sendMessage(conversationId: number, content: string): void {
    if (!this.client?.connected) {
      throw new Error('Chat is not connected');
    }

    this.client.publish({
      destination: `/app/chat/${conversationId}`,
      body: JSON.stringify({ content }),
    });
  }

  private setupAppStateListener(): void {
    this.appStateSubscription?.remove();
    this.appStateSubscription = AppState.addEventListener('change', (state: AppStateStatus) => {
      if (state === 'active' && this.token && this.client && !this.client.active) {
        this.client.activate();
      }
    });
  }

  private ensureTopicSubscription(conversationId: number): void {
    if (!this.client?.connected || this.topicSubscriptions.has(conversationId)) {
      return;
    }

    const subscription = this.client.subscribe(
      `/topic/conversation/${conversationId}`,
      (message: IMessage) => {
        const payload = JSON.parse(message.body) as ChatMessageBroadcast;
        this.messageHandlers.get(conversationId)?.forEach((handler) => handler(payload));
      }
    );

    this.topicSubscriptions.set(conversationId, subscription);
  }

  private subscribeToErrors(): void {
    if (!this.client?.connected) {
      return;
    }

    this.errorSubscription?.unsubscribe();
    this.errorSubscription = this.client.subscribe('/user/queue/errors', (message: IMessage) => {
      try {
        const body = JSON.parse(message.body) as { message?: string };
        this.errorHandler?.(body.message ?? message.body);
      } catch {
        this.errorHandler?.(message.body);
      }
    });
  }

  private resubscribeAll(): void {
    this.topicSubscriptions.forEach((subscription) => subscription.unsubscribe());
    this.topicSubscriptions.clear();
    this.errorSubscription?.unsubscribe();
    this.errorSubscription = null;

    for (const conversationId of this.messageHandlers.keys()) {
      this.ensureTopicSubscription(conversationId);
    }

    this.subscribeToErrors();
  }
}

export const chatClient = new ChatWebSocketClient();
