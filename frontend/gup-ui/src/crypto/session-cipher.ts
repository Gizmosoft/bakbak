import {
  deserializeRatchetState,
  initAlice,
  initBob,
  ratchetDecrypt,
  ratchetEncrypt,
  serializeRatchetState,
  type RatchetState,
} from './double-ratchet';
import { base64ToBytes, bytesToBase64, utf8Decode, utf8Encode } from './encoding';
import {
  generateX25519KeyPair,
  type KeyPair,
} from './noble-primitives';
import { x3dhInitiate, x3dhRespond, type PreKeyBundle } from './x3dh';

export type SignalWireMessage = {
  v: 1;
  t: 'prekey' | 'msg';
  registrationId?: number;
  ik?: string;
  ek?: string;
  spkId?: number;
  opkId?: number | null;
  dh: string;
  pn: number;
  n: number;
  ct: string;
};

export type SessionRecord = {
  ratchet: RatchetState;
  pendingPreKey: boolean;
  remoteIdentityKey: Uint8Array;
  localRegistrationId: number;
};

export function serializeSession(session: SessionRecord): string {
  return JSON.stringify({
    ratchet: serializeRatchetState(session.ratchet),
    pendingPreKey: session.pendingPreKey,
    remoteIdentityKey: bytesToBase64(session.remoteIdentityKey),
    localRegistrationId: session.localRegistrationId,
  });
}

export function deserializeSession(raw: string): SessionRecord {
  const parsed = JSON.parse(raw) as {
    ratchet: string;
    pendingPreKey: boolean;
    remoteIdentityKey: string;
    localRegistrationId: number;
  };
  return {
    ratchet: deserializeRatchetState(parsed.ratchet),
    pendingPreKey: parsed.pendingPreKey,
    remoteIdentityKey: base64ToBytes(parsed.remoteIdentityKey),
    localRegistrationId: parsed.localRegistrationId,
  };
}

export function createOutboundSession(
  ourIdentity: KeyPair,
  ourRegistrationId: number,
  bundle: PreKeyBundle
): { session: SessionRecord; firstMessageMeta: Omit<SignalWireMessage, 'dh' | 'pn' | 'n' | 'ct'> } {
  const ephemeral = generateX25519KeyPair();
  const x3dh = x3dhInitiate(ourIdentity, ephemeral, bundle);
  const ratchet = initAlice(x3dh.sharedSecret, bundle.signedPreKey.publicKey);

  return {
    session: {
      ratchet,
      pendingPreKey: true,
      remoteIdentityKey: bundle.identityKey,
      localRegistrationId: ourRegistrationId,
    },
    firstMessageMeta: {
      v: 1,
      t: 'prekey',
      registrationId: ourRegistrationId,
      ik: bytesToBase64(ourIdentity.publicKey),
      ek: bytesToBase64(ephemeral.publicKey),
      spkId: x3dh.usedSignedPreKeyId,
      opkId: x3dh.usedOneTimePreKeyId,
    },
  };
}

export function encryptMessage(
  session: SessionRecord,
  plaintext: string,
  ourIdentityPublic?: Uint8Array
): string {
  const { header, ciphertext } = ratchetEncrypt(session.ratchet, utf8Encode(plaintext));

  let wire: SignalWireMessage;
  if (session.pendingPreKey) {
    if (!ourIdentityPublic) {
      throw new Error('Identity public key required for PreKey message');
    }
    // pendingPreKey fields are expected to already be on the session via createOutboundSession;
    // callers wrap with meta — here we only emit ratchet fields for subsequent msgs.
    // First encrypt after createOutboundSession should use encryptPreKeyMessage.
    wire = {
      v: 1,
      t: 'msg',
      dh: bytesToBase64(header.dh),
      pn: header.pn,
      n: header.n,
      ct: bytesToBase64(ciphertext),
    };
  } else {
    wire = {
      v: 1,
      t: 'msg',
      dh: bytesToBase64(header.dh),
      pn: header.pn,
      n: header.n,
      ct: bytesToBase64(ciphertext),
    };
  }

  session.pendingPreKey = false;
  return JSON.stringify(wire);
}

export function encryptPreKeyMessage(
  session: SessionRecord,
  plaintext: string,
  meta: Omit<SignalWireMessage, 'dh' | 'pn' | 'n' | 'ct'>
): string {
  const { header, ciphertext } = ratchetEncrypt(session.ratchet, utf8Encode(plaintext));
  const wire: SignalWireMessage = {
    ...meta,
    dh: bytesToBase64(header.dh),
    pn: header.pn,
    n: header.n,
    ct: bytesToBase64(ciphertext),
  };
  session.pendingPreKey = false;
  return JSON.stringify(wire);
}

export function createInboundSessionFromPreKey(
  ourIdentity: KeyPair,
  ourRegistrationId: number,
  ourSignedPreKey: KeyPair,
  ourOneTimePreKey: KeyPair | null,
  wire: SignalWireMessage
): SessionRecord {
  if (wire.t !== 'prekey' || !wire.ik || !wire.ek) {
    throw new Error('Not a PreKey message');
  }

  const theirIdentity = base64ToBytes(wire.ik);
  const theirEphemeral = base64ToBytes(wire.ek);
  const sharedSecret = x3dhRespond(
    ourIdentity,
    ourSignedPreKey,
    theirIdentity,
    theirEphemeral,
    ourOneTimePreKey
  );

  return {
    ratchet: initBob(sharedSecret, ourSignedPreKey),
    pendingPreKey: false,
    remoteIdentityKey: theirIdentity,
    localRegistrationId: ourRegistrationId,
  };
}

export function decryptMessage(session: SessionRecord, content: string): string {
  const wire = JSON.parse(content) as SignalWireMessage;
  if (wire.v !== 1) {
    throw new Error(`Unsupported Signal message version: ${wire.v}`);
  }
  const plaintext = ratchetDecrypt(
    session.ratchet,
    {
      dh: base64ToBytes(wire.dh),
      pn: wire.pn,
      n: wire.n,
    },
    base64ToBytes(wire.ct)
  );
  return utf8Decode(plaintext);
}

export function parseWireMessage(content: string): SignalWireMessage {
  return JSON.parse(content) as SignalWireMessage;
}
