import { AppState, type AppStateStatus } from 'react-native';

import { getStompHost } from '@/config/env';
import type { ChatMessageBroadcast } from '@/types/message';
import {
  buildStompFrame,
  parseIncomingPayload,
  STOMP_SUBPROTOCOLS,
  type StompFrame,
} from './stomp-framing';
import {
  buildSockJsWebSocketUrl,
  getSockJsHttpBase,
  isSockJsOpenFrame,
  unwrapSockJs,
  wrapSockJs,
} from './sockjs';

export type ChatMessageHandler = (message: ChatMessageBroadcast) => void;
export type ChatErrorHandler = (message: string) => void;

type ConnectionListener = (connected: boolean) => void;
type StatusListener = (status: string | null) => void;

const RECONNECT_DELAY_MS = 3_000;
const CONNECT_TIMEOUT_MS = 15_000;

class ChatWebSocketClient {
  private ws: WebSocket | null = null;
  private token: string | null = null;
  private connected = false;
  private sockJsOpen = false;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private connectTimeoutTimer: ReturnType<typeof setTimeout> | null = null;
  private subscribedConversations = new Set<number>();
  private messageHandlers = new Map<number, Set<ChatMessageHandler>>();
  private errorHandler: ChatErrorHandler | null = null;
  private appStateSubscription: { remove: () => void } | null = null;
  private connectionListeners = new Set<ConnectionListener>();
  private statusListeners = new Set<StatusListener>();
  private shouldReconnect = false;
  private statusMessage: string | null = 'Connecting to chat…';

  onConnectionChange(listener: ConnectionListener): () => void {
    this.connectionListeners.add(listener);
    listener(this.connected);
    return () => {
      this.connectionListeners.delete(listener);
    };
  }

  onStatusChange(listener: StatusListener): () => void {
    this.statusListeners.add(listener);
    listener(this.statusMessage);
    return () => {
      this.statusListeners.delete(listener);
    };
  }

  getStatusMessage(): string | null {
    return this.statusMessage;
  }

  setErrorHandler(handler: ChatErrorHandler | null): void {
    this.errorHandler = handler;
  }

  connect(token: string): void {
    if (this.connected && this.token === token && this.ws?.readyState === WebSocket.OPEN) {
      return;
    }

    this.shouldReconnect = true;
    void this.openSocket(token);
    this.setupAppStateListener();
  }

  disconnect(): void {
    this.shouldReconnect = false;
    this.clearReconnectTimer();
    this.clearConnectTimeout();
    this.appStateSubscription?.remove();
    this.appStateSubscription = null;

    if (this.ws) {
      try {
        if (this.connected) {
          this.sendRaw(buildStompFrame('DISCONNECT', {}));
        }
        this.ws.close();
      } catch {
        // ignore close errors
      }
      this.ws = null;
    }

    this.token = null;
    this.sockJsOpen = false;
    this.subscribedConversations.clear();
    this.setConnected(false);
    this.setStatus(null);
  }

  isConnected(): boolean {
    return this.connected;
  }

  subscribeToConversation(conversationId: number, handler: ChatMessageHandler): () => void {
    const handlers = this.messageHandlers.get(conversationId) ?? new Set<ChatMessageHandler>();
    handlers.add(handler);
    this.messageHandlers.set(conversationId, handlers);

    if (this.connected) {
      this.ensureTopicSubscription(conversationId);
    }

    return () => {
      const currentHandlers = this.messageHandlers.get(conversationId);
      currentHandlers?.delete(handler);
      if (!currentHandlers || currentHandlers.size === 0) {
        this.messageHandlers.delete(conversationId);
        this.subscribedConversations.delete(conversationId);
      }
    };
  }

  sendMessage(conversationId: number, content: string): void {
    if (!this.connected || !this.ws) {
      throw new Error('Chat is not connected');
    }

    const body = JSON.stringify({ content });
    this.sendRaw(
      buildStompFrame(
        'SEND',
        {
          destination: `/app/chat/${conversationId}`,
          'content-type': 'application/json',
        },
        body
      )
    );
  }

  private async openSocket(token: string): Promise<void> {
    this.clearReconnectTimer();
    this.clearConnectTimeout();

    if (this.ws) {
      try {
        this.ws.close();
      } catch {
        // ignore
      }
      this.ws = null;
    }

    this.token = token;
    this.sockJsOpen = false;
    this.setConnected(false);
    this.setStatus('Connecting to chat…');

    try {
      const wsUrl = await buildSockJsWebSocketUrl(getSockJsHttpBase());
      const socket = new WebSocket(wsUrl, [...STOMP_SUBPROTOCOLS]);
      this.ws = socket;

      socket.onopen = () => {
        this.setStatus('Opening SockJS session…');
      };

      socket.onmessage = (event) => {
        void this.handleWebSocketMessage(event.data);
      };

      socket.onerror = () => {
        const message = 'WebSocket connection error';
        this.setStatus(message);
        this.errorHandler?.(message);
      };

      socket.onclose = () => {
        this.ws = null;
        this.sockJsOpen = false;
        this.subscribedConversations.clear();
        this.setConnected(false);
        if (this.shouldReconnect) {
          this.setStatus('Reconnecting…');
        }
        this.scheduleReconnect();
      };

      this.connectTimeoutTimer = setTimeout(() => {
        if (!this.connected) {
          const message = 'Chat connection timed out — check backend and network';
          this.setStatus(message);
          this.errorHandler?.(message);
          try {
            socket.close();
          } catch {
            // ignore
          }
        }
      }, CONNECT_TIMEOUT_MS);
    } catch (error) {
      const message =
        error instanceof Error ? error.message : 'Failed to start chat connection';
      this.setStatus(message);
      this.errorHandler?.(message);
      this.scheduleReconnect();
    }
  }

  private async handleWebSocketMessage(data: unknown): Promise<void> {
    const raw = await this.readMessageData(data);

    if (!this.sockJsOpen) {
      if (isSockJsOpenFrame(raw)) {
        this.sockJsOpen = true;
        this.sendStompConnect();
      }
      return;
    }

    try {
      const payloads = unwrapSockJs(raw);
      for (const payload of payloads) {
        for (const frame of parseIncomingPayload(payload)) {
          this.handleStompFrame(frame);
        }
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to parse chat message';
      this.setStatus(message);
      this.errorHandler?.(message);
    }
  }

  private sendStompConnect(): void {
    if (!this.token) {
      return;
    }

    this.setStatus('Authenticating chat session…');
    this.sendRaw(
      buildStompFrame('CONNECT', {
        'accept-version': '1.2,1.1,1.0',
        'heart-beat': '10000,10000',
        host: getStompHost(),
        Authorization: `Bearer ${this.token}`,
      })
    );
  }

  private async readMessageData(data: unknown): Promise<string> {
    if (typeof data === 'string') {
      return data;
    }

    if (data instanceof ArrayBuffer) {
      return new TextDecoder().decode(data);
    }

    if (ArrayBuffer.isView(data)) {
      return new TextDecoder().decode(data.buffer);
    }

    if (typeof Blob !== 'undefined' && data instanceof Blob) {
      return data.text();
    }

    return String(data ?? '');
  }

  private handleStompFrame(frame: StompFrame): void {
    const command = frame.command.toUpperCase();

    switch (command) {
      case 'CONNECTED':
        this.clearConnectTimeout();
        this.setConnected(true);
        this.setStatus(null);
        this.resubscribeAll();
        return;

      case 'MESSAGE':
        this.handleIncomingMessage(frame);
        return;

      case 'ERROR':
        this.clearConnectTimeout();
        this.setConnected(false);
        {
          const message = frame.headers.message ?? frame.body ?? 'STOMP error';
          this.setStatus(message);
          this.errorHandler?.(message);
        }
        return;

      default:
        return;
    }
  }

  private handleIncomingMessage(frame: StompFrame): void {
    const destination = frame.headers.destination ?? '';

    if (destination.includes('/queue/errors')) {
      try {
        const body = JSON.parse(frame.body) as { message?: string };
        this.errorHandler?.(body.message ?? frame.body);
      } catch {
        this.errorHandler?.(frame.body);
      }
      return;
    }

    const match = destination.match(/\/topic\/conversation\/(\d+)/);
    if (!match) {
      return;
    }

    const conversationId = Number(match[1]);
    const payload = JSON.parse(frame.body) as ChatMessageBroadcast;
    this.messageHandlers.get(conversationId)?.forEach((handler) => handler(payload));
  }

  private ensureTopicSubscription(conversationId: number): void {
    if (!this.connected || this.subscribedConversations.has(conversationId)) {
      return;
    }

    this.sendRaw(
      buildStompFrame('SUBSCRIBE', {
        id: `sub-${conversationId}`,
        destination: `/topic/conversation/${conversationId}`,
      })
    );
    this.subscribedConversations.add(conversationId);
  }

  private subscribeToErrors(): void {
    if (!this.connected) {
      return;
    }

    this.sendRaw(
      buildStompFrame('SUBSCRIBE', {
        id: 'sub-errors',
        destination: '/user/queue/errors',
      })
    );
  }

  private resubscribeAll(): void {
    this.subscribedConversations.clear();

    for (const conversationId of this.messageHandlers.keys()) {
      this.ensureTopicSubscription(conversationId);
    }

    this.subscribeToErrors();
  }

  private sendRaw(frame: string): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      throw new Error('WebSocket is not open');
    }

    this.ws.send(wrapSockJs(frame));
  }

  private setConnected(connected: boolean): void {
    if (this.connected === connected) {
      return;
    }
    this.connected = connected;
    this.connectionListeners.forEach((listener) => listener(connected));
  }

  private setStatus(status: string | null): void {
    this.statusMessage = status;
    this.statusListeners.forEach((listener) => listener(status));
  }

  private scheduleReconnect(): void {
    if (!this.shouldReconnect || !this.token || this.reconnectTimer) {
      return;
    }

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      if (this.shouldReconnect && this.token) {
        void this.openSocket(this.token);
      }
    }, RECONNECT_DELAY_MS);
  }

  private clearReconnectTimer(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  private clearConnectTimeout(): void {
    if (this.connectTimeoutTimer) {
      clearTimeout(this.connectTimeoutTimer);
      this.connectTimeoutTimer = null;
    }
  }

  private setupAppStateListener(): void {
    this.appStateSubscription?.remove();
    this.appStateSubscription = AppState.addEventListener('change', (state: AppStateStatus) => {
      if (
        state === 'active' &&
        this.shouldReconnect &&
        this.token &&
        (!this.ws || this.ws.readyState === WebSocket.CLOSED)
      ) {
        void this.openSocket(this.token);
      }
    });
  }
}

export const chatClient = new ChatWebSocketClient();
