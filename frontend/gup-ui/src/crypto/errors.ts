/**
 * Facade-facing crypto errors for UI / sync layers.
 */
export class SignalCryptoError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'SignalCryptoError';
  }
}

export class PeerKeysMissingError extends SignalCryptoError {
  constructor(userId: number) {
    super(`Recipient ${userId} has not published encryption keys yet`);
    this.name = 'PeerKeysMissingError';
  }
}

export class IdentityMismatchError extends SignalCryptoError {
  constructor(userId: number) {
    super(`Peer ${userId} identity key changed — session reset required`);
    this.name = 'IdentityMismatchError';
  }
}
