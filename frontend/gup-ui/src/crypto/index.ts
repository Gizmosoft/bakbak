import './polyfill';

export * from './errors';
export * from './safety-number';
export {
  decryptFromPeer,
  encryptForPeer,
  ensureKeysPublished,
  replenishOneTimePreKeysIfNeeded,
  resetSessionWithPeer,
} from './signal-protocol-service';
