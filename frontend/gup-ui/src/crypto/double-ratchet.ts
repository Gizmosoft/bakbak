import {
  aesGcmDecrypt,
  aesGcmEncrypt,
  dh,
  generateX25519KeyPair,
  kdfCk,
  kdfRk,
  type KeyPair,
} from './noble-primitives';
import { bytesToBase64, base64ToBytes } from './encoding';

export const MAX_SKIP = 100;

export type RatchetHeader = {
  dh: Uint8Array;
  pn: number;
  n: number;
};

export type RatchetState = {
  dhs: KeyPair;
  dhr: Uint8Array | null;
  rk: Uint8Array;
  cks: Uint8Array | null;
  ckr: Uint8Array | null;
  ns: number;
  nr: number;
  pn: number;
  mkskipped: Record<string, string>;
};

function skippedKey(dhPublic: Uint8Array, n: number): string {
  return `${bytesToBase64(dhPublic)}:${n}`;
}

export function serializeRatchetState(state: RatchetState): string {
  return JSON.stringify({
    dhs: {
      publicKey: bytesToBase64(state.dhs.publicKey),
      privateKey: bytesToBase64(state.dhs.privateKey),
    },
    dhr: state.dhr ? bytesToBase64(state.dhr) : null,
    rk: bytesToBase64(state.rk),
    cks: state.cks ? bytesToBase64(state.cks) : null,
    ckr: state.ckr ? bytesToBase64(state.ckr) : null,
    ns: state.ns,
    nr: state.nr,
    pn: state.pn,
    mkskipped: state.mkskipped,
  });
}

export function deserializeRatchetState(raw: string): RatchetState {
  const parsed = JSON.parse(raw) as {
    dhs: { publicKey: string; privateKey: string };
    dhr: string | null;
    rk: string;
    cks: string | null;
    ckr: string | null;
    ns: number;
    nr: number;
    pn: number;
    mkskipped: Record<string, string>;
  };
  return {
    dhs: {
      publicKey: base64ToBytes(parsed.dhs.publicKey),
      privateKey: base64ToBytes(parsed.dhs.privateKey),
    },
    dhr: parsed.dhr ? base64ToBytes(parsed.dhr) : null,
    rk: base64ToBytes(parsed.rk),
    cks: parsed.cks ? base64ToBytes(parsed.cks) : null,
    ckr: parsed.ckr ? base64ToBytes(parsed.ckr) : null,
    ns: parsed.ns,
    nr: parsed.nr,
    pn: parsed.pn,
    mkskipped: parsed.mkskipped ?? {},
  };
}

/** Alice initializes after X3DH using Bob's signed prekey as the remote DH public. */
export function initAlice(sharedSecret: Uint8Array, bobRatchetPublic: Uint8Array): RatchetState {
  const dhs = generateX25519KeyPair();
  const dhOut = dh(dhs.privateKey, bobRatchetPublic);
  const [rk, cks] = kdfRk(sharedSecret, dhOut);
  return {
    dhs,
    dhr: bobRatchetPublic,
    rk,
    cks,
    ckr: null,
    ns: 0,
    nr: 0,
    pn: 0,
    mkskipped: {},
  };
}

/** Bob initializes after X3DH with his signed prekey as the local DH key pair. */
export function initBob(sharedSecret: Uint8Array, bobSignedPreKey: KeyPair): RatchetState {
  return {
    dhs: bobSignedPreKey,
    dhr: null,
    rk: sharedSecret,
    cks: null,
    ckr: null,
    ns: 0,
    nr: 0,
    pn: 0,
    mkskipped: {},
  };
}

function skipMessageKeys(state: RatchetState, until: number): void {
  if (state.ckr == null) {
    return;
  }
  if (until - state.nr > MAX_SKIP) {
    throw new Error('Too many skipped message keys');
  }
  while (state.nr < until) {
    const [ckr, mk] = kdfCk(state.ckr);
    state.ckr = ckr;
    if (state.dhr) {
      state.mkskipped[skippedKey(state.dhr, state.nr)] = bytesToBase64(mk);
    }
    state.nr += 1;
  }
}

function dhRatchet(state: RatchetState, theirDh: Uint8Array): void {
  state.pn = state.ns;
  state.ns = 0;
  state.nr = 0;
  state.dhr = theirDh;

  const dhOutRecv = dh(state.dhs.privateKey, theirDh);
  const [rkRecv, ckr] = kdfRk(state.rk, dhOutRecv);
  state.rk = rkRecv;
  state.ckr = ckr;

  state.dhs = generateX25519KeyPair();
  const dhOutSend = dh(state.dhs.privateKey, theirDh);
  const [rkSend, cks] = kdfRk(state.rk, dhOutSend);
  state.rk = rkSend;
  state.cks = cks;
}

export function ratchetEncrypt(
  state: RatchetState,
  plaintext: Uint8Array
): { header: RatchetHeader; ciphertext: Uint8Array } {
  if (state.cks == null) {
    throw new Error('Sending chain not initialized');
  }
  const [cks, mk] = kdfCk(state.cks);
  state.cks = cks;
  const header: RatchetHeader = {
    dh: state.dhs.publicKey,
    pn: state.pn,
    n: state.ns,
  };
  state.ns += 1;
  return { header, ciphertext: aesGcmEncrypt(mk, plaintext) };
}

export function ratchetDecrypt(
  state: RatchetState,
  header: RatchetHeader,
  ciphertext: Uint8Array
): Uint8Array {
  const skipped = state.mkskipped[skippedKey(header.dh, header.n)];
  if (skipped) {
    delete state.mkskipped[skippedKey(header.dh, header.n)];
    return aesGcmDecrypt(base64ToBytes(skipped), ciphertext);
  }

  if (state.dhr == null || bytesToBase64(header.dh) !== bytesToBase64(state.dhr)) {
    if (state.dhr) {
      skipMessageKeys(state, header.pn);
    }
    dhRatchet(state, header.dh);
  }

  skipMessageKeys(state, header.n);
  if (state.ckr == null) {
    throw new Error('Receiving chain not initialized');
  }
  const [ckr, mk] = kdfCk(state.ckr);
  state.ckr = ckr;
  state.nr += 1;
  return aesGcmDecrypt(mk, ciphertext);
}
