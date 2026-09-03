import { useQueryClient } from '@tanstack/react-query';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { formatMessageTime } from '@/lib/format';
import { MessageAttachment } from '@/features/chat/components/MessageAttachment';
import type { MessageResponse, MessageStatus } from '@/types/message';
import { retryFailedMessage } from '@/websocket/message-sync';

type MessageBubbleProps = {
  message: MessageResponse;
  isOwnMessage: boolean;
  senderId?: number;
};

function statusIndicator(status: MessageStatus): string {
  switch (status) {
    case 'SENDING':
      return '◷';
    case 'SENT':
      return '✓';
    case 'DELIVERED':
      return '✓✓';
    case 'FAILED':
      return '✗';
    default:
      return '';
  }
}

export function MessageBubble({ message, isOwnMessage, senderId }: MessageBubbleProps) {
  const queryClient = useQueryClient();
  const indicator = isOwnMessage ? statusIndicator(message.status) : '';
  const isFailed = isOwnMessage && message.status === 'FAILED';

  const handleRetry = () => {
    if (!senderId || !isFailed) {
      return;
    }
    void retryFailedMessage(queryClient, message, senderId);
  };

  const bubble = (
    <View style={[styles.bubble, isOwnMessage ? styles.bubbleOwn : styles.bubbleOther]}>
      {message.attachment ? (
        <MessageAttachment attachment={message.attachment} isOwnMessage={isOwnMessage} />
      ) : null}
      {message.content ? (
        <Text style={[styles.content, isOwnMessage ? styles.contentOwn : styles.contentOther]}>
          {message.content}
        </Text>
      ) : null}
      <View style={styles.metaRow}>
        <Text style={[styles.time, isOwnMessage ? styles.timeOwn : styles.timeOther]}>
          {formatMessageTime(message.sentAt)}
        </Text>
        {indicator ? (
          <Text
            style={[
              styles.status,
              isOwnMessage ? styles.statusOwn : styles.statusOther,
              message.status === 'FAILED' && styles.statusFailed,
            ]}
          >
            {indicator}
          </Text>
        ) : null}
      </View>
    </View>
  );

  return (
    <View style={[styles.row, isOwnMessage ? styles.rowOwn : styles.rowOther]}>
      {isFailed ? (
        <Pressable onPress={handleRetry} accessibilityRole="button" accessibilityLabel="Retry send">
          {bubble}
        </Pressable>
      ) : (
        bubble
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    paddingHorizontal: 12,
    paddingVertical: 4,
    flexDirection: 'row',
  },
  rowOwn: {
    justifyContent: 'flex-end',
  },
  rowOther: {
    justifyContent: 'flex-start',
  },
  bubble: {
    maxWidth: '80%',
    borderRadius: 16,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  bubbleOwn: {
    backgroundColor: '#1A1B3A',
    borderBottomRightRadius: 4,
  },
  bubbleOther: {
    backgroundColor: '#eef0f4',
    borderBottomLeftRadius: 4,
  },
  content: {
    fontSize: 16,
    lineHeight: 22,
  },
  contentOwn: {
    color: '#fff',
  },
  contentOther: {
    color: '#1A1B3A',
  },
  metaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'flex-end',
    gap: 6,
    marginTop: 4,
  },
  time: {
    fontSize: 11,
  },
  timeOwn: {
    color: 'rgba(255,255,255,0.75)',
  },
  timeOther: {
    color: '#888',
  },
  status: {
    fontSize: 11,
    fontWeight: '600',
  },
  statusOwn: {
    color: 'rgba(255,255,255,0.75)',
  },
  statusOther: {
    color: '#888',
  },
  statusFailed: {
    color: '#ff8a80',
  },
});
