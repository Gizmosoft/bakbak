import { sha256 } from '@noble/hashes/sha2.js';

import { concatBytes } from './encoding';

/**
 * Numeric safety number from two identity public keys (order-independent).
 * Display formatting (grouped digits) is left to UI.
 */
export function computeSafetyNumber(
  localIdentityPublic: Uint8Array,
  remoteIdentityPublic: Uint8Array
): string {
  const [a, b] =
    compareBytes(localIdentityPublic, remoteIdentityPublic) <= 0
      ? [localIdentityPublic, remoteIdentityPublic]
      : [remoteIdentityPublic, localIdentityPublic];

  const digest = sha256(concatBytes(a, b));
  let digits = '';
  for (let i = 0; i < 30; i++) {
    digits += String(digest[i]! % 10);
  }
  return digits;
}

function compareBytes(a: Uint8Array, b: Uint8Array): number {
  const len = Math.min(a.length, b.length);
  for (let i = 0; i < len; i++) {
    if (a[i]! !== b[i]!) {
      return a[i]! - b[i]!;
    }
  }
  return a.length - b.length;
}

export function formatSafetyNumber(digits: string): string {
  return digits.replace(/(\d{5})(?=\d)/g, '$1 ').trim();
}
