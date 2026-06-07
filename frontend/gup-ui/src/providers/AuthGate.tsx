import { Stack, useRouter, useSegments, type Href } from 'expo-router';
import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';

import { LoadingState } from '@/components/ui/LoadingState';
import { useAuth } from '@/providers/AuthProvider';

/**
 * Redirects based on session: unauthenticated users → (auth), authenticated → (app).
 * Renders a loading state while AuthProvider restores the session from secure storage.
 */
export function AuthGate() {
  const { isAuthenticated, isLoading } = useAuth();
  const segments = useSegments();
  const router = useRouter();

  useEffect(() => {
    if (isLoading) {
      return;
    }

    const inAuthGroup = String(segments[0]) === '(auth)';

    if (!isAuthenticated && !inAuthGroup) {
      router.replace('/login' as Href);
    } else if (isAuthenticated && inAuthGroup) {
      router.replace('/' as Href);
    }
  }, [isAuthenticated, isLoading, segments, router]);

  if (isLoading) {
    return (
      <View style={styles.loading}>
        <LoadingState message="Restoring session…" />
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
