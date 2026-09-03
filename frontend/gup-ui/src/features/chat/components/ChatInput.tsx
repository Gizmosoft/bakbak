import { useState } from 'react';
import * as DocumentPicker from 'expo-document-picker';
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
  type LayoutChangeEvent,
} from 'react-native';

import {
  ATTACHMENT,
  formatFileSize,
  isAllowedMimeType,
} from '@/constants/attachments';
import { VALIDATION } from '@/constants/validation';
import type { OutboundAttachment } from '@/websocket/message-sync';

export const CHAT_INPUT_NATIVE_ID = 'chat-input';

type ChatInputProps = {
  onSend: (content: string, attachment?: OutboundAttachment) => Promise<void>;
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
  const [pendingAttachment, setPendingAttachment] = useState<OutboundAttachment | null>(null);

  const canSend =
    (value.trim().length > 0 || pendingAttachment != null) && !sendDisabled && !isSending;
  const showCounter = value.length >= SHOW_COUNTER_THRESHOLD;

  const handlePickAttachment = async () => {
    setError(null);
    try {
      const result = await DocumentPicker.getDocumentAsync({
        copyToCacheDirectory: true,
        multiple: false,
      });

      if (result.canceled || result.assets.length === 0) {
        return;
      }

      const asset = result.assets[0];
      const mimeType = asset.mimeType ?? 'application/octet-stream';

      if (!isAllowedMimeType(mimeType)) {
        setError('This file type is not supported');
        return;
      }

      let sizeBytes = asset.size ?? 0;
      if (sizeBytes <= 0) {
        const fileResponse = await fetch(asset.uri);
        const blob = await fileResponse.blob();
        sizeBytes = blob.size;
      }

      if (sizeBytes <= 0) {
        setError('Could not determine file size');
        return;
      }

      if (sizeBytes > ATTACHMENT.maxSizeBytes) {
        setError(`File must be under ${formatFileSize(ATTACHMENT.maxSizeBytes)}`);
        return;
      }

      setPendingAttachment({
        uri: asset.uri,
        mimeType,
        sizeBytes,
        fileName: asset.name,
      });
    } catch (pickError) {
      setError(pickError instanceof Error ? pickError.message : 'Failed to pick file');
    }
  };

  const handleSend = async () => {
    const content = value.trim();
    if (!content && !pendingAttachment) {
      return;
    }

    setError(null);
    setIsSending(true);
    const attachmentToSend = pendingAttachment;
    try {
      await onSend(content, attachmentToSend ?? undefined);
      onChangeText('');
      setPendingAttachment(null);
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
      {pendingAttachment ? (
        <View style={styles.attachmentPreview}>
          <View style={styles.attachmentMeta}>
            <Text style={styles.attachmentName} numberOfLines={1}>
              📎 {pendingAttachment.fileName}
            </Text>
            <Text style={styles.attachmentSize}>
              {formatFileSize(pendingAttachment.sizeBytes)}
            </Text>
          </View>
          <Pressable
            onPress={() => setPendingAttachment(null)}
            accessibilityRole="button"
            accessibilityLabel="Remove attachment"
          >
            <Text style={styles.removeAttachment}>✕</Text>
          </Pressable>
        </View>
      ) : null}
      <View style={styles.row}>
        <Pressable
          style={[styles.attachButton, (sendDisabled || isSending) && styles.attachButtonDisabled]}
          onPress={handlePickAttachment}
          disabled={sendDisabled || isSending || pendingAttachment != null}
          accessibilityRole="button"
          accessibilityLabel="Attach file"
        >
          <Text style={styles.attachLabel}>📎</Text>
        </Pressable>
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
  attachmentPreview: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#f3f4f8',
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 8,
    marginBottom: 8,
  },
  attachmentMeta: {
    flex: 1,
    marginRight: 8,
  },
  attachmentName: {
    color: '#1A1B3A',
    fontSize: 14,
    fontWeight: '600',
  },
  attachmentSize: {
    color: '#666',
    fontSize: 12,
    marginTop: 2,
  },
  removeAttachment: {
    color: '#888',
    fontSize: 16,
    padding: 4,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 8,
  },
  attachButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#f0f0f0',
  },
  attachButtonDisabled: {
    opacity: 0.5,
  },
  attachLabel: {
    fontSize: 18,
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
