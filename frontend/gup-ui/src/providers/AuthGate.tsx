import { Stack, useRouter, useSegments, type Href } from 'expo-router';
import { useEffect } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';

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
        <ActivityIndicator size="large" color="#1A1B3A" />
      </View>
    );
  }

  return <Stack screenOptions={{ headerShown: false }} />;
}

const styles = StyleSheet.create({
  loading: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#fff',
  },
});
