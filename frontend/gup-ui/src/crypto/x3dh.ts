import {
  dh,
  hkdfSha256,
  identityPrivateToX25519,
  identityPublicToX25519,
  type KeyPair,
  verify,
} from './noble-primitives';
import { concatBytes } from './encoding';

export type PreKeyBundle = {
  identityKey: Uint8Array;
  registrationId: number;
  signedPreKey: {
    keyId: number;
    publicKey: Uint8Array;
    signature: Uint8Array;
  };
  oneTimePreKey?: {
    keyId: number;
    publicKey: Uint8Array;
  } | null;
};

export type X3dhResult = {
  sharedSecret: Uint8Array;
  ephemeralKeyPair: KeyPair;
  usedSignedPreKeyId: number;
  usedOneTimePreKeyId: number | null;
};

/**
 * X3DH as initiator (Alice). Verifies Bob's signed prekey, then derives SK.
 */
export function x3dhInitiate(
  ourIdentity: KeyPair,
  ourEphemeral: KeyPair,
  bundle: PreKeyBundle
): X3dhResult {
  if (
    !verify(bundle.signedPreKey.signature, bundle.signedPreKey.publicKey, bundle.identityKey)
  ) {
    throw new Error('Invalid signed prekey signature');
  }

  const ikA = identityPrivateToX25519(ourIdentity.privateKey);
  const ikB = identityPublicToX25519(bundle.identityKey);

  const dh1 = dh(ikA, bundle.signedPreKey.publicKey);
  const dh2 = dh(ourEphemeral.privateKey, ikB);
  const dh3 = dh(ourEphemeral.privateKey, bundle.signedPreKey.publicKey);

  let ikm = concatBytes(new Uint8Array(32).fill(0xff), dh1, dh2, dh3);
  let usedOneTimePreKeyId: number | null = null;

  if (bundle.oneTimePreKey) {
    const dh4 = dh(ourEphemeral.privateKey, bundle.oneTimePreKey.publicKey);
    ikm = concatBytes(ikm, dh4);
    usedOneTimePreKeyId = bundle.oneTimePreKey.keyId;
  }

  const sharedSecret = hkdfSha256(ikm, 32, 'WhisperText');

  return {
    sharedSecret,
    ephemeralKeyPair: ourEphemeral,
    usedSignedPreKeyId: bundle.signedPreKey.keyId,
    usedOneTimePreKeyId,
  };
}

/**
 * X3DH as responder (Bob) reconstructing SK from Alice's PreKey message.
 */
export function x3dhRespond(
  ourIdentity: KeyPair,
  ourSignedPreKey: KeyPair,
  theirIdentityPublic: Uint8Array,
  theirEphemeralPublic: Uint8Array,
  ourOneTimePreKey: KeyPair | null
): Uint8Array {
  const ikB = identityPrivateToX25519(ourIdentity.privateKey);
  const ikA = identityPublicToX25519(theirIdentityPublic);

  const dh1 = dh(ourSignedPreKey.privateKey, ikA);
  const dh2 = dh(ikB, theirEphemeralPublic);
  const dh3 = dh(ourSignedPreKey.privateKey, theirEphemeralPublic);

  let ikm = concatBytes(new Uint8Array(32).fill(0xff), dh1, dh2, dh3);
  if (ourOneTimePreKey) {
    const dh4 = dh(ourOneTimePreKey.privateKey, theirEphemeralPublic);
    ikm = concatBytes(ikm, dh4);
  }

  return hkdfSha256(ikm, 32, 'WhisperText');
}
