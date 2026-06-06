const DEFAULT_API_URL = 'http://localhost:8080';

/** Base URL for REST calls (no trailing slash). */
export function getApiBaseUrl(): string {
  const url = process.env.EXPO_PUBLIC_API_URL ?? DEFAULT_API_URL;
  return url.replace(/\/$/, '');
}

/** WebSocket URL for raw STOMP (React Native uses /ws-native, not SockJS /ws). */
export function getWebSocketNativeUrl(): string {
  const base = getApiBaseUrl();
  const wsBase = base.replace(/^https/, 'wss').replace(/^http/, 'ws');
  return `${wsBase}/ws-native`;
}

/** @deprecated Use getWebSocketNativeUrl for the mobile STOMP client. */
export function getWebSocketUrl(): string {
  return getWebSocketNativeUrl();
}
