import { Stack } from 'expo-router';

/**
 * Layout for the (app) route group — screens shown after login.
 * The (app) folder name is not part of the URL (e.g. index.tsx → /).
 */
export default function AppLayout() {
  return <Stack screenOptions={{ headerShown: false }} />;
}
