import * as SecureStore from 'expo-secure-store';

const AUTH_TOKEN_KEY = 'gup.auth.token';

/** Read the persisted JWT, or null if logged out / first launch. */
export async function getAuthToken(): Promise<string | null> {
  return SecureStore.getItemAsync(AUTH_TOKEN_KEY);
}

/** Persist the JWT after register or login. */
export async function setAuthToken(token: string): Promise<void> {
  await SecureStore.setItemAsync(AUTH_TOKEN_KEY, token);
}

/** Remove the JWT on logout or after a 401. */
export async function clearAuthToken(): Promise<void> {
  await SecureStore.deleteItemAsync(AUTH_TOKEN_KEY);
}
