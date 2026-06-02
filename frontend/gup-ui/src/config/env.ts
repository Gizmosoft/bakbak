const DEFAULT_API_URL = 'http://localhost:8080';

/** Base URL for REST calls (no trailing slash). */
export function getApiBaseUrl(): string {
  const url = process.env.EXPO_PUBLIC_API_URL ?? DEFAULT_API_URL;
  return url.replace(/\/$/, '');
}

/** WebSocket URL derived from the API base (http → ws, https → wss) + /ws. */
export function getWebSocketUrl(): string {
  const base = getApiBaseUrl();
  const wsBase = base.replace(/^http/, 'ws');
  return `${wsBase}/ws`;
}
