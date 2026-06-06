import { Stack } from 'expo-router';

/**
 * Layout for the (auth) route group — login and register screens.
 * The (auth) folder name is not part of the URL (e.g. login.tsx → /login).
 */
export default function AuthLayout() {
  // Stack navigator for auth screens; headers hidden (each screen owns its UI).
  return <Stack screenOptions={{ headerShown: false }} />;
}
