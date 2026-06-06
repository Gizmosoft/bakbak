export const STOMP_SUBPROTOCOLS = ['v12.stomp', 'v11.stomp', 'v10.stomp'] as const;
export const STOMP_NULL = '\x00';

export type StompFrame = {
  command: string;
  headers: Record<string, string>;
  body: string;
};

/** Build a STOMP 1.2 frame terminated with a null octet (matches websocket_client.py). */
export function buildStompFrame(
  command: string,
  headers: Record<string, string>,
  body?: string
): string {
  const frameHeaders = { ...headers };
  const bodyText = body ?? '';

  if (body !== undefined && frameHeaders['content-length'] === undefined) {
    frameHeaders['content-length'] = String(new TextEncoder().encode(bodyText).length);
  }

  const headerLines = [command, ...Object.entries(frameHeaders).map(([key, value]) => `${key}:${value}`)];
  const headerBlock = headerLines.join('\n');

  if (body !== undefined) {
    return `${headerBlock}\n\n${bodyText}${STOMP_NULL}`;
  }

  return `${headerBlock}\n\n${STOMP_NULL}`;
}

/** Parse one STOMP frame from a raw WebSocket payload. */
export function parseStompFrame(raw: string): StompFrame {
  const text = raw.replace(/\x00+$/, '');
  const separator = text.indexOf('\n\n');
  const head = separator >= 0 ? text.slice(0, separator) : text;
  const body = separator >= 0 ? text.slice(separator + 2) : '';

  const lines = head.split('\n');
  const command = lines[0]?.trim() ?? '';
  const headers: Record<string, string> = {};

  for (const line of lines.slice(1)) {
    const colon = line.indexOf(':');
    if (colon >= 0) {
      headers[line.slice(0, colon)] = line.slice(colon + 1);
    }
  }

  return { command, headers, body };
}

export function parseIncomingPayload(raw: string): StompFrame[] {
  if (!raw || raw === STOMP_NULL) {
    return [];
  }

  if (raw === '\n') {
    return [];
  }

  return [parseStompFrame(raw)];
}
