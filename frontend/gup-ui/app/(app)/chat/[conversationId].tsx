import { router, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ChatInput } from '@/features/chat/components/ChatInput';
import { MessageBubble } from '@/features/chat/components/MessageBubble';
import { useChatConnection } from '@/features/chat/hooks/useChatConnection';
import { useChatSubscription } from '@/features/chat/hooks/useChatSubscription';
import { flattenMessages, useMessages } from '@/features/chat/hooks/useMessages';
import { sendMessageSchema } from '@/features/chat/schemas';
import { useConversations } from '@/features/conversations/hooks/useConversations';
import { useAuth } from '@/providers/AuthProvider';
import { chatClient } from '@/websocket/chat.client';
import type { MessageResponse } from '@/types/message';

/** Chat route (/chat/:conversationId). Message history + real-time send/receive. */
export default function ChatScreen() {
  const { conversationId: rawConversationId } = useLocalSearchParams<{ conversationId: string }>();
  const conversationId = Number(rawConversationId);
  const { user } = useAuth();
  const { data: conversations } = useConversations();
  const {
    data,
    isLoading,
    isError,
    error,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    refetch,
  } = useMessages(conversationId);
  const [chatError, setChatError] = useState<string | null>(null);
  const { isConnected } = useChatConnection();

  useChatSubscription(conversationId);

  useEffect(() => {
    chatClient.setErrorHandler((message) => setChatError(message));
    return () => chatClient.setErrorHandler(null);
  }, []);

  const conversation = conversations?.find((item) => item.conversationId === conversationId);
  const headerTitle =
    conversation?.otherUser.displayName ??
    conversation?.otherUser.username ??
    'Chat';

  const messages = useMemo(
    () => flattenMessages(data?.pages) as MessageResponse[],
    [data?.pages]
  );

  const handleSend = useCallback(
    async (content: string) => {
      const parsed = sendMessageSchema.safeParse({ content });
      if (!parsed.success) {
        throw new Error(parsed.error.issues[0]?.message ?? 'Invalid message');
      }

      setChatError(null);
      chatClient.sendMessage(conversationId, parsed.data.content);
    },
    [conversationId]
  );

  const handleLoadOlder = () => {
    if (hasNextPage && !isFetchingNextPage) {
      void fetchNextPage();
    }
  };

  if (!Number.isFinite(conversationId) || conversationId <= 0) {
    return (
      <SafeAreaView style={styles.safeArea}>
        <View style={styles.centered}>
          <Text style={styles.errorText}>Invalid conversation</Text>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safeArea} edges={['top', 'left', 'right', 'bottom']}>
      <View style={styles.header}>
        <Pressable onPress={() => router.back()} style={styles.backButton}>
          <Text style={styles.backText}>Back</Text>
        </Pressable>
        <Text style={styles.title} numberOfLines={1}>
          {headerTitle}
        </Text>
        <View style={styles.headerSpacer} />
      </View>

      {chatError ? <Text style={styles.bannerError}>{chatError}</Text> : null}

      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        keyboardVerticalOffset={Platform.OS === 'ios' ? 8 : 0}
      >
        {isLoading ? (
          <View style={styles.centered}>
            <ActivityIndicator size="large" color="#1A1B3A" />
          </View>
        ) : isError ? (
          <View style={styles.centered}>
            <Text style={styles.errorText}>{error?.message ?? 'Failed to load messages'}</Text>
            <Pressable onPress={() => refetch()}>
              <Text style={styles.retryText}>Retry</Text>
            </Pressable>
          </View>
        ) : (
          <FlatList
            style={styles.messageList}
            data={[...messages].reverse()}
            inverted
            keyboardShouldPersistTaps="handled"
            keyboardDismissMode="interactive"
            keyExtractor={(item) => String(item.id)}
            renderItem={({ item }) => (
              <MessageBubble message={item} isOwnMessage={item.senderId === user?.id} />
            )}
            onEndReached={handleLoadOlder}
            onEndReachedThreshold={0.2}
            ListFooterComponent={
              isFetchingNextPage ? (
                <View style={styles.loaderFooter}>
                  <ActivityIndicator color="#1A1B3A" />
                </View>
              ) : null
            }
            contentContainerStyle={messages.length === 0 ? styles.emptyMessages : undefined}
            ListEmptyComponent={
              <View style={styles.emptyMessagesInner}>
                <Text style={styles.emptyText}>No messages yet. Say hello!</Text>
              </View>
            }
          />
        )}

        <ChatInput
          onSend={handleSend}
          sendDisabled={!isConnected}
          hint={isConnected ? null : 'Connecting to chat…'}
        />
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#fff',
  },
  flex: {
    flex: 1,
  },
  messageList: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#e0e0e0',
  },
  backButton: {
    width: 60,
  },
  backText: {
    color: '#1A1B3A',
    fontWeight: '600',
    fontSize: 16,
  },
  title: {
    flex: 1,
    textAlign: 'center',
    fontSize: 18,
    fontWeight: '700',
    color: '#1A1B3A',
  },
  headerSpacer: {
    width: 60,
  },
  bannerError: {
    backgroundColor: '#fdecea',
    color: '#c62828',
    paddingHorizontal: 16,
    paddingVertical: 8,
    fontSize: 13,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  errorText: {
    color: '#c62828',
    textAlign: 'center',
    marginBottom: 8,
  },
  retryText: {
    color: '#1A1B3A',
    fontWeight: '600',
  },
  loaderFooter: {
    paddingVertical: 12,
  },
  emptyMessages: {
    flexGrow: 1,
    justifyContent: 'center',
  },
  emptyMessagesInner: {
    padding: 24,
    alignItems: 'center',
  },
  emptyText: {
    color: '#666',
  },
});
