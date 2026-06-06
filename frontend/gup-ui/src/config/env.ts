const DEFAULT_API_URL = 'http://localhost:8080';

/** Base URL for REST calls (no trailing slash). */
export function getApiBaseUrl(): string {
  const url = process.env.EXPO_PUBLIC_API_URL ?? DEFAULT_API_URL;
  return url.replace(/\/$/, '');
}

/** Host header for STOMP CONNECT (hostname from API URL). */
export function getStompHost(): string {
  return new URL(getApiBaseUrl()).hostname;
}

/** Raw STOMP WebSocket URL — matches backend clients/websocket_client.py ws-native mode. */
export function getWebSocketNativeUrl(): string {
  const base = getApiBaseUrl();
  const wsBase = base.replace(/^https/, 'wss').replace(/^http/, 'ws');
  return `${wsBase}/ws-native`;
}

/** @deprecated Use getWebSocketNativeUrl for the mobile STOMP client. */
export function getWebSocketUrl(): string {
  return getWebSocketNativeUrl();
}
