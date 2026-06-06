import { StyleSheet, Text, View } from 'react-native';

import { formatMessageTime } from '@/lib/format';
import type { MessageResponse } from '@/types/message';

type MessageBubbleProps = {
  message: MessageResponse;
  isOwnMessage: boolean;
};

export function MessageBubble({ message, isOwnMessage }: MessageBubbleProps) {
  return (
    <View style={[styles.row, isOwnMessage ? styles.rowOwn : styles.rowOther]}>
      <View style={[styles.bubble, isOwnMessage ? styles.bubbleOwn : styles.bubbleOther]}>
        <Text style={[styles.content, isOwnMessage ? styles.contentOwn : styles.contentOther]}>
          {message.content}
        </Text>
        <Text style={[styles.time, isOwnMessage ? styles.timeOwn : styles.timeOther]}>
          {formatMessageTime(message.createdAt)}
        </Text>
      </View>
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
  time: {
    marginTop: 4,
    fontSize: 11,
  },
  timeOwn: {
    color: 'rgba(255,255,255,0.75)',
    textAlign: 'right',
  },
  timeOther: {
    color: '#888',
  },
});
