/**
 * @noble/* requires Web Crypto's crypto.getRandomValues.
 * Expo Go / React Native do not define it globally — bridge via expo-crypto.
 */
import * as ExpoCrypto from 'expo-crypto';

type CryptoLike = {
  getRandomValues: <T extends ArrayBufferView>(array: T) => T;
};

const root = globalThis as typeof globalThis & { crypto?: CryptoLike };

if (root.crypto == null) {
  (root as { crypto: CryptoLike }).crypto = {} as CryptoLike;
}

if (typeof root.crypto.getRandomValues !== 'function') {
  root.crypto.getRandomValues = ExpoCrypto.getRandomValues.bind(ExpoCrypto) as CryptoLike['getRandomValues'];
}
