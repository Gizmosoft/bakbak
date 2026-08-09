/** Shared encoding helpers for Signal Protocol wire and storage. */

const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

export function bytesToBase64(bytes: Uint8Array): string {
  let out = '';
  for (let i = 0; i < bytes.length; i += 3) {
    const a = bytes[i]!;
    const b = i + 1 < bytes.length ? bytes[i + 1]! : 0;
    const c = i + 2 < bytes.length ? bytes[i + 2]! : 0;
    out += B64[a >> 2];
    out += B64[((a & 3) << 4) | (b >> 4)];
    out += i + 1 < bytes.length ? B64[((b & 15) << 2) | (c >> 6)] : '=';
    out += i + 2 < bytes.length ? B64[c & 63] : '=';
  }
  return out;
}

export function base64ToBytes(b64: string): Uint8Array {
  const cleaned = b64.replace(/=+$/, '');
  const len = cleaned.length;
  const out = new Uint8Array((b64.length * 3) / 4 - (b64.endsWith('==') ? 2 : b64.endsWith('=') ? 1 : 0));
  let o = 0;
  const lookup = new Uint8Array(256);
  for (let i = 0; i < B64.length; i++) {
    lookup[B64.charCodeAt(i)] = i;
  }
  for (let i = 0; i < len; i += 4) {
    const a = lookup[cleaned.charCodeAt(i)]!;
    const b = lookup[cleaned.charCodeAt(i + 1)]!;
    const c = i + 2 < len ? lookup[cleaned.charCodeAt(i + 2)]! : 0;
    const d = i + 3 < len ? lookup[cleaned.charCodeAt(i + 3)]! : 0;
    out[o++] = (a << 2) | (b >> 4);
    if (i + 2 < len) {
      out[o++] = ((b & 15) << 4) | (c >> 2);
    }
    if (i + 3 < len) {
      out[o++] = ((c & 3) << 6) | d;
    }
  }
  return out.subarray(0, o);
}

export function concatBytes(...parts: Uint8Array[]): Uint8Array {
  const total = parts.reduce((n, p) => n + p.length, 0);
  const out = new Uint8Array(total);
  let offset = 0;
  for (const part of parts) {
    out.set(part, offset);
    offset += part.length;
  }
  return out;
}

export function utf8Encode(text: string): Uint8Array {
  return new TextEncoder().encode(text);
}

export function utf8Decode(bytes: Uint8Array): string {
  return new TextDecoder().decode(bytes);
}

export function constantTimeEqual(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) {
    return false;
  }
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a[i]! ^ b[i]!;
  }
  return diff === 0;
}
