import { router, type Href } from 'expo-router';
import { FlatList, Pressable, RefreshControl, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { LoadingState } from '@/components/ui/LoadingState';
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
        <LoadingState message="Loading conversations…" />
      ) : isError ? (
        <ErrorState
          message={error?.message ?? 'Failed to load conversations'}
          onRetry={() => refetch()}
        />
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
            <EmptyState
              title="No conversations yet"
              subtitle="Search for someone to start chatting"
              actionLabel="Find people"
              onAction={() => router.push('/search' as Href)}
            />
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
  emptyList: {
    flexGrow: 1,
  },
});
