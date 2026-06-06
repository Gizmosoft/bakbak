import { StatusBar } from 'expo-status-bar';

import { AuthGate } from '@/providers/AuthGate';
import { AuthProvider } from '@/providers/AuthProvider';
import { ChatConnectionProvider } from '@/providers/ChatConnectionProvider';
import { ChatDraftProvider } from '@/providers/ChatDraftProvider';
import { QueryProvider } from '@/providers/QueryProvider';

/** Root layout: auth, server state, chat socket, and navigation gate. */
export default function RootLayout() {
  return (
    <QueryProvider>
      <AuthProvider>
        <ChatDraftProvider>
          <ChatConnectionProvider>
            <AuthGate />
            <StatusBar style="auto" />
          </ChatConnectionProvider>
        </ChatDraftProvider>
      </AuthProvider>
    </QueryProvider>
  );
}
