import { Pressable, StyleSheet, Text, View } from 'react-native';

import { formatMessageTime, getInitials } from '@/lib/format';

import type { ConversationListRow } from '@/features/conversations/lib/merge-conversations-with-drafts';

type ConversationListItemProps = {
  conversation: ConversationListRow;
  onPress: () => void;
};

export function ConversationListItem({ conversation, onPress }: ConversationListItemProps) {
  const displayName = conversation.otherUser.displayName ?? conversation.otherUser.username;
  const preview = conversation.draftPreview
    ? `Draft: ${conversation.draftPreview}`
    : conversation.lastMessagePreview ??
      conversation.lastMessage?.content ??
      'No messages yet';
  const isDraftPreview = Boolean(conversation.draftPreview);
  const isOnline = conversation.otherUserPresence === 'ONLINE';

  return (
    <Pressable style={styles.row} onPress={onPress}>
      <View style={styles.avatarWrap}>
        <View style={styles.avatar}>
          <Text style={styles.avatarText}>{getInitials(displayName)}</Text>
        </View>
        {isOnline ? <View style={styles.onlineDot} /> : null}
      </View>
      <View style={styles.content}>
        <View style={styles.topLine}>
          <Text style={styles.name} numberOfLines={1}>
            {displayName}
          </Text>
          <Text style={styles.time}>{formatMessageTime(conversation.lastMessageAt)}</Text>
        </View>
        <Text
          style={[styles.preview, isDraftPreview && styles.draftPreview]}
          numberOfLines={1}
        >
          {preview}
        </Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#e0e0e0',
    backgroundColor: '#fff',
  },
  avatarWrap: {
    marginRight: 12,
    position: 'relative',
  },
  avatar: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: '#1A1B3A',
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: {
    color: '#fff',
    fontWeight: '700',
    fontSize: 16,
  },
  onlineDot: {
    position: 'absolute',
    right: 0,
    bottom: 0,
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: '#2ecc71',
    borderWidth: 2,
    borderColor: '#fff',
  },
  content: {
    flex: 1,
  },
  topLine: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 4,
  },
  name: {
    flex: 1,
    fontSize: 16,
    fontWeight: '600',
    color: '#1A1B3A',
    marginRight: 8,
  },
  time: {
    fontSize: 12,
    color: '#888',
  },
  preview: {
    fontSize: 14,
    color: '#666',
  },
  draftPreview: {
    fontStyle: 'italic',
    color: '#888',
  },
});
