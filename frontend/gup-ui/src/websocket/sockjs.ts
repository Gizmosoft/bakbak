import { getApiBaseUrl } from '@/config/env';

const SOCKJS_WS_SUFFIX = '/websocket';

function randomSessionId(length: number): string {
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
  let result = '';
  for (let i = 0; i < length; i += 1) {
    result += chars[Math.floor(Math.random() * chars.length)];
  }
  return result;
}

/** HTTP SockJS base, e.g. http://10.0.0.90:8080/ws */
export function getSockJsHttpBase(): string {
  return `${getApiBaseUrl()}/ws`;
}

/** Perform /info handshake and return the WebSocket transport URL. */
export async function buildSockJsWebSocketUrl(httpWsBase: string): Promise<string> {
  const base = httpWsBase.replace(/\/$/, '');
  const infoUrl = `${base}/info`;

  const response = await fetch(infoUrl, {
    headers: { Accept: 'application/json' },
  });

  if (!response.ok) {
    throw new Error(`SockJS info request failed (${response.status})`);
  }

  const info = (await response.json()) as { websocket?: boolean };
  if (info.websocket === false) {
    throw new Error('Server does not support SockJS WebSocket transport');
  }

  const parsed = new URL(base);
  const wsScheme = parsed.protocol === 'https:' ? 'wss' : 'ws';
  const serverId = String(Math.floor(Math.random() * 1000)).padStart(3, '0');
  const sessionId = randomSessionId(8);
  const path = parsed.pathname || '/ws';

  return `${wsScheme}://${parsed.host}${path}/${serverId}/${sessionId}${SOCKJS_WS_SUFFIX}`;
}

/** Client → server SockJS WebSocket encoding (JSON array only). */
export function wrapSockJs(stompFrame: string): string {
  return JSON.stringify([stompFrame]);
}

/** Decode SockJS WebSocket frames into STOMP payload strings. */
export function unwrapSockJs(raw: string): string[] {
  if (!raw || raw === 'o' || raw === 'h') {
    return [];
  }

  if (raw.startsWith('c')) {
    throw new Error(`SockJS closed: ${raw}`);
  }

  if (raw.startsWith('a')) {
    const messages = JSON.parse(raw.slice(1)) as unknown;
    if (!Array.isArray(messages)) {
      return [];
    }
    return messages.filter((message): message is string => typeof message === 'string');
  }

  if (raw.startsWith('[')) {
    const messages = JSON.parse(raw) as unknown;
    if (!Array.isArray(messages)) {
      return [];
    }
    return messages.filter((message): message is string => typeof message === 'string');
  }

  return [raw];
}

export function isSockJsOpenFrame(raw: string): boolean {
  return raw === 'o';
}
