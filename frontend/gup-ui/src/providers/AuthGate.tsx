import { Stack, useRouter, useSegments, type Href } from 'expo-router';
import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';

import { LoadingState } from '@/components/ui/LoadingState';
import { useAuth } from '@/providers/AuthProvider';

/**
 * Redirects based on session: unauthenticated users → (auth), authenticated → (app).
 * Waits for SQLite bootstrap before rendering the app stack.
 */
export function AuthGate() {
  const { isAuthenticated, isLoading, isStoreReady } = useAuth();
  const segments = useSegments();
  const router = useRouter();

  useEffect(() => {
    if (isLoading || (isAuthenticated && !isStoreReady)) {
      return;
    }

    const inAuthGroup = String(segments[0]) === '(auth)';

    if (!isAuthenticated && !inAuthGroup) {
      router.replace('/login' as Href);
    } else if (isAuthenticated && inAuthGroup) {
      router.replace('/' as Href);
    }
  }, [isAuthenticated, isLoading, isStoreReady, segments, router]);

  if (isLoading || (isAuthenticated && !isStoreReady)) {
    return (
      <View style={styles.loading}>
        <LoadingState
          message={isLoading ? 'Restoring session…' : 'Syncing local messages…'}
        />
      </View>
    );
  }

  return <Stack screenOptions={{ headerShown: false }} />;
}

const styles = StyleSheet.create({
  loading: {
    flex: 1,
    backgroundColor: '#fff',
  },
});
