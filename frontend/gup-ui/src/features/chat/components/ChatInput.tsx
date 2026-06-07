import { useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
  type LayoutChangeEvent,
} from 'react-native';

import { VALIDATION } from '@/constants/validation';

export const CHAT_INPUT_NATIVE_ID = 'chat-input';

type ChatInputProps = {
  onSend: (content: string) => Promise<void>;
  value: string;
  onChangeText: (text: string) => void;
  /** When true, sending is blocked but the user can still type. */
  sendDisabled?: boolean;
  hint?: string | null;
  onLayout?: (event: LayoutChangeEvent) => void;
};

const { max: maxLength } = VALIDATION.message;
const SHOW_COUNTER_THRESHOLD = maxLength - 500;

export function ChatInput({
  onSend,
  value,
  onChangeText,
  sendDisabled = false,
  hint = null,
  onLayout,
}: ChatInputProps) {
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canSend = value.trim().length > 0 && !sendDisabled && !isSending;
  const showCounter = value.length >= SHOW_COUNTER_THRESHOLD;

  const handleSend = async () => {
    const content = value.trim();
    if (!content) {
      return;
    }

    setError(null);
    setIsSending(true);
    try {
      await onSend(content);
      onChangeText('');
    } catch (sendError) {
      setError(sendError instanceof Error ? sendError.message : 'Failed to send message');
    } finally {
      setIsSending(false);
    }
  };

  return (
    <View style={styles.container} onLayout={onLayout}>
      {hint ? <Text style={styles.hint}>{hint}</Text> : null}
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <View style={styles.row}>
        <TextInput
          nativeID={CHAT_INPUT_NATIVE_ID}
          style={styles.input}
          value={value}
          onChangeText={onChangeText}
          placeholder="Message..."
          placeholderTextColor="#999"
          multiline
          maxLength={maxLength}
          editable={!isSending}
        />
        <Pressable
          style={[styles.sendButton, !canSend && styles.sendButtonDisabled]}
          onPress={handleSend}
          disabled={!canSend}
        >
          {isSending ? (
            <ActivityIndicator color="#fff" size="small" />
          ) : (
            <Text style={styles.sendLabel}>Send</Text>
          )}
        </Pressable>
      </View>
      {showCounter ? (
        <Text style={[styles.counter, value.length >= maxLength && styles.counterLimit]}>
          {value.length}/{maxLength}
        </Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#ddd',
    backgroundColor: '#fff',
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  error: {
    color: '#c62828',
    fontSize: 12,
    marginBottom: 6,
  },
  hint: {
    color: '#888',
    fontSize: 12,
    marginBottom: 6,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 8,
  },
  input: {
    flex: 1,
    minHeight: 40,
    maxHeight: 120,
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 20,
    paddingHorizontal: 14,
    paddingVertical: 10,
    fontSize: 16,
    color: '#1A1B3A',
    backgroundColor: '#fafafa',
  },
  sendButton: {
    backgroundColor: '#1A1B3A',
    borderRadius: 20,
    paddingHorizontal: 16,
    paddingVertical: 10,
    minWidth: 64,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sendButtonDisabled: {
    opacity: 0.5,
  },
  sendLabel: {
    color: '#fff',
    fontWeight: '600',
    fontSize: 14,
  },
  counter: {
    marginTop: 4,
    fontSize: 11,
    color: '#888',
    textAlign: 'right',
  },
  counterLimit: {
    color: '#c62828',
  },
});
