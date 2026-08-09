import { ed25519, x25519 } from '@noble/curves/ed25519.js';
import { gcm } from '@noble/ciphers/aes.js';
import { hkdf } from '@noble/hashes/hkdf.js';
import { hmac } from '@noble/hashes/hmac.js';
import { sha256 } from '@noble/hashes/sha2.js';
import { randomBytes } from '@noble/hashes/utils.js';

import { concatBytes } from './encoding';

export type KeyPair = {
  publicKey: Uint8Array;
  privateKey: Uint8Array;
};

export function generateIdentityKeyPair(): KeyPair {
  const { secretKey, publicKey } = ed25519.keygen();
  return { publicKey, privateKey: secretKey };
}

export function generateX25519KeyPair(): KeyPair {
  const { secretKey, publicKey } = x25519.keygen();
  return { publicKey, privateKey: secretKey };
}

export function identityPublicToX25519(edPublic: Uint8Array): Uint8Array {
  return ed25519.utils.toMontgomery(edPublic);
}

export function identityPrivateToX25519(edPrivate: Uint8Array): Uint8Array {
  return ed25519.utils.toMontgomerySecret(edPrivate);
}

export function sign(message: Uint8Array, identityPrivate: Uint8Array): Uint8Array {
  return ed25519.sign(message, identityPrivate);
}

export function verify(
  signature: Uint8Array,
  message: Uint8Array,
  identityPublic: Uint8Array
): boolean {
  return ed25519.verify(signature, message, identityPublic);
}

export function dh(privateKey: Uint8Array, publicKey: Uint8Array): Uint8Array {
  return x25519.getSharedSecret(privateKey, publicKey);
}

export function hkdfSha256(
  ikm: Uint8Array,
  length: number,
  info: string | Uint8Array,
  salt?: Uint8Array
): Uint8Array {
  const infoBytes = typeof info === 'string' ? new TextEncoder().encode(info) : info;
  return hkdf(sha256, ikm, salt ?? new Uint8Array(32), infoBytes, length);
}

export function hmacSha256(key: Uint8Array, data: Uint8Array): Uint8Array {
  return hmac(sha256, key, data);
}

/** Signal-style KDF_RK: returns [newRootKey, chainKey]. */
export function kdfRk(rootKey: Uint8Array, dhOutput: Uint8Array): [Uint8Array, Uint8Array] {
  const okm = hkdfSha256(dhOutput, 64, 'WhisperRatchet', rootKey);
  return [okm.subarray(0, 32), okm.subarray(32, 64)];
}

/** Signal-style KDF_CK: returns [newChainKey, messageKey]. */
export function kdfCk(chainKey: Uint8Array): [Uint8Array, Uint8Array] {
  const messageKey = hmacSha256(chainKey, new Uint8Array([0x01]));
  const nextChainKey = hmacSha256(chainKey, new Uint8Array([0x02]));
  return [nextChainKey, messageKey];
}

export function aesGcmEncrypt(messageKey: Uint8Array, plaintext: Uint8Array): Uint8Array {
  const nonce = randomBytes(12);
  const ciphertext = gcm(messageKey, nonce).encrypt(plaintext);
  return concatBytes(nonce, ciphertext);
}

export function aesGcmDecrypt(messageKey: Uint8Array, payload: Uint8Array): Uint8Array {
  if (payload.length < 12 + 16) {
    throw new Error('Ciphertext too short');
  }
  const nonce = payload.subarray(0, 12);
  const ciphertext = payload.subarray(12);
  return gcm(messageKey, nonce).decrypt(ciphertext);
}

export function randomRegistrationId(): number {
  const bytes = randomBytes(2);
  return ((bytes[0]! << 8) | bytes[1]!) & 0x3fff;
}

export { randomBytes };
