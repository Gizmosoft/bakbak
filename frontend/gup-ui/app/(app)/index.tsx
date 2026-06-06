import { router, type Href } from 'expo-router';
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  RefreshControl,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ConversationListItem } from '@/features/conversations/components/ConversationListItem';
import { useConversationList } from '@/features/conversations/hooks/useConversationList';
import { useAuth } from '@/providers/AuthProvider';

/** Authenticated home route (/). Contact window — list of 1:1 conversations. */
export default function ConversationsScreen() {
  const { logout } = useAuth();
  const { rows, isLoading, isError, error, refetch, isRefetching } = useConversationList();

  const handleOpenChat = (conversationId: number) => {
    router.push(`/chat/${conversationId}` as Href);
  };

  const handleLogout = async () => {
    await logout();
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <View style={styles.header}>
        <Text style={styles.title}>Chats</Text>
        <View style={styles.headerActions}>
          <Pressable onPress={() => router.push('/search' as Href)} style={styles.headerButton}>
            <Text style={styles.headerButtonText}>Search</Text>
          </Pressable>
          <Pressable onPress={handleLogout} style={styles.headerButton}>
            <Text style={styles.headerButtonText}>Log out</Text>
          </Pressable>
        </View>
      </View>

      {isLoading ? (
        <View style={styles.centered}>
          <ActivityIndicator size="large" color="#1A1B3A" />
        </View>
      ) : isError ? (
        <View style={styles.centered}>
          <Text style={styles.errorText}>{error?.message ?? 'Failed to load conversations'}</Text>
          <Pressable style={styles.retryButton} onPress={() => refetch()}>
            <Text style={styles.retryText}>Retry</Text>
          </Pressable>
        </View>
      ) : (
        <FlatList
          data={rows}
          keyExtractor={(item) => String(item.conversationId)}
          renderItem={({ item }) => (
            <ConversationListItem
              conversation={item}
              onPress={() => handleOpenChat(item.conversationId)}
            />
          )}
          refreshControl={<RefreshControl refreshing={isRefetching} onRefresh={refetch} />}
          ListEmptyComponent={
            <View style={styles.centered}>
              <Text style={styles.emptyTitle}>No conversations yet</Text>
              <Text style={styles.emptySubtitle}>Search for someone to start chatting</Text>
              <Pressable
                style={styles.primaryButton}
                onPress={() => router.push('/search' as Href)}
              >
                <Text style={styles.primaryButtonText}>Find people</Text>
              </Pressable>
            </View>
          }
          contentContainerStyle={rows.length === 0 ? styles.emptyList : undefined}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#fff',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#e0e0e0',
  },
  title: {
    fontSize: 24,
    fontWeight: '700',
    color: '#1A1B3A',
  },
  headerActions: {
    flexDirection: 'row',
    gap: 12,
  },
  headerButton: {
    paddingVertical: 4,
    paddingHorizontal: 2,
  },
  headerButtonText: {
    color: '#1A1B3A',
    fontWeight: '600',
    fontSize: 14,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  emptyList: {
    flexGrow: 1,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1A1B3A',
    marginBottom: 8,
  },
  emptySubtitle: {
    fontSize: 14,
    color: '#666',
    marginBottom: 16,
    textAlign: 'center',
  },
  primaryButton: {
    backgroundColor: '#1A1B3A',
    borderRadius: 8,
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  primaryButtonText: {
    color: '#fff',
    fontWeight: '600',
  },
  errorText: {
    color: '#c62828',
    textAlign: 'center',
    marginBottom: 12,
  },
  retryButton: {
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  retryText: {
    color: '#1A1B3A',
    fontWeight: '600',
  },
});
