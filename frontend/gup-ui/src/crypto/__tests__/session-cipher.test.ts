import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
  createInboundSessionFromPreKey,
  createOutboundSession,
  decryptMessage,
  encryptMessage,
  encryptPreKeyMessage,
  parseWireMessage,
  serializeSession,
  deserializeSession,
} from '../session-cipher';
import {
  generateIdentityKeyPair,
  generateX25519KeyPair,
  randomRegistrationId,
  sign,
} from '../noble-primitives';
import { computeSafetyNumber, formatSafetyNumber } from '../safety-number';
import type { PreKeyBundle } from '../x3dh';

function makeBundle(
  bobIdentity: ReturnType<typeof generateIdentityKeyPair>,
  includeOtpk: boolean
): { bundle: PreKeyBundle; signedPreKey: ReturnType<typeof generateX25519KeyPair>; otpk: ReturnType<typeof generateX25519KeyPair> | null } {
  const signedPreKey = generateX25519KeyPair();
  const signature = sign(signedPreKey.publicKey, bobIdentity.privateKey);
  const otpk = includeOtpk ? generateX25519KeyPair() : null;
  const bundle: PreKeyBundle = {
    identityKey: bobIdentity.publicKey,
    registrationId: randomRegistrationId(),
    signedPreKey: {
      keyId: 1,
      publicKey: signedPreKey.publicKey,
      signature,
    },
    oneTimePreKey: otpk
      ? {
          keyId: 7,
          publicKey: otpk.publicKey,
        }
      : null,
  };
  return { bundle, signedPreKey, otpk };
}

describe('Signal Protocol session', () => {
  it('establishes a session with X3DH + Double Ratchet and exchanges messages', () => {
    const alice = generateIdentityKeyPair();
    const bob = generateIdentityKeyPair();
    const aliceReg = randomRegistrationId();
    const bobReg = randomRegistrationId();
    const { bundle, signedPreKey, otpk } = makeBundle(bob, true);

    const { session: aliceSession, firstMessageMeta } = createOutboundSession(
      alice,
      aliceReg,
      bundle
    );
    const prekeyCipher = encryptPreKeyMessage(aliceSession, 'hello bob', firstMessageMeta);
    const wire = parseWireMessage(prekeyCipher);

    const bobSession = createInboundSessionFromPreKey(
      bob,
      bobReg,
      signedPreKey,
      otpk,
      wire
    );
    assert.equal(decryptMessage(bobSession, prekeyCipher), 'hello bob');

    const reply = encryptMessage(bobSession, 'hello alice');
    assert.equal(decryptMessage(aliceSession, reply), 'hello alice');

    const followUp = encryptMessage(aliceSession, 'how are you?');
    assert.equal(decryptMessage(bobSession, followUp), 'how are you?');
  });

  it('handles out-of-order messages via skipped keys', () => {
    const alice = generateIdentityKeyPair();
    const bob = generateIdentityKeyPair();
    const { bundle, signedPreKey, otpk } = makeBundle(bob, true);

    const { session: aliceSession, firstMessageMeta } = createOutboundSession(
      alice,
      1,
      bundle
    );
    const m1 = encryptPreKeyMessage(aliceSession, 'one', firstMessageMeta);
    const bobSession = createInboundSessionFromPreKey(
      bob,
      2,
      signedPreKey,
      otpk,
      parseWireMessage(m1)
    );
    assert.equal(decryptMessage(bobSession, m1), 'one');

    // Bob sends so Alice advances, then Alice sends two messages.
    assert.equal(decryptMessage(aliceSession, encryptMessage(bobSession, 'ack')), 'ack');

    const a2 = encryptMessage(aliceSession, 'two');
    const a3 = encryptMessage(aliceSession, 'three');

    assert.equal(decryptMessage(bobSession, a3), 'three');
    assert.equal(decryptMessage(bobSession, a2), 'two');
  });

  it('round-trips session serialization', () => {
    const alice = generateIdentityKeyPair();
    const bob = generateIdentityKeyPair();
    const { bundle, signedPreKey, otpk } = makeBundle(bob, false);

    const { session: aliceSession, firstMessageMeta } = createOutboundSession(
      alice,
      3,
      bundle
    );
    const cipher = encryptPreKeyMessage(aliceSession, 'persist', firstMessageMeta);
    const bobSession = createInboundSessionFromPreKey(
      bob,
      4,
      signedPreKey,
      otpk,
      parseWireMessage(cipher)
    );
    assert.equal(decryptMessage(bobSession, cipher), 'persist');

    const restored = deserializeSession(serializeSession(aliceSession));
    const next = encryptMessage(restored, 'again');
    assert.equal(decryptMessage(bobSession, next), 'again');
  });

  it('computes a stable safety number', () => {
    const a = generateIdentityKeyPair();
    const b = generateIdentityKeyPair();
    const n1 = computeSafetyNumber(a.publicKey, b.publicKey);
    const n2 = computeSafetyNumber(b.publicKey, a.publicKey);
    assert.equal(n1, n2);
    assert.equal(n1.length, 30);
    assert.match(formatSafetyNumber(n1), /^\d{5}( \d{5}){5}$/);
  });
});
