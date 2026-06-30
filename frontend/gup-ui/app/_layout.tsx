import 'react-native-gesture-handler';

import { StatusBar } from 'expo-status-bar';
import { KeyboardProvider } from 'react-native-keyboard-controller';

import { AuthGate } from '@/providers/AuthGate';
import { AuthProvider } from '@/providers/AuthProvider';
import { ChatConnectionProvider } from '@/providers/ChatConnectionProvider';
import { ChatDraftProvider } from '@/providers/ChatDraftProvider';
import { DatabaseProvider } from '@/providers/DatabaseProvider';
import { QueryProvider } from '@/providers/QueryProvider';

/** Root layout: local DB, auth, server state, chat socket, and navigation gate. */
export default function RootLayout() {
  return (
    <KeyboardProvider preload={false}>
      <QueryProvider>
        <DatabaseProvider>
          <AuthProvider>
            <ChatDraftProvider>
              <ChatConnectionProvider>
                <AuthGate />
                <StatusBar style="auto" />
              </ChatConnectionProvider>
            </ChatDraftProvider>
          </AuthProvider>
        </DatabaseProvider>
      </QueryProvider>
    </KeyboardProvider>
  );
}
