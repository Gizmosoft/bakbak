import { useState } from 'react';
import {
  ActivityIndicator,
  Image,
  Linking,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';

import { formatFileSize, isImageMimeType } from '@/constants/attachments';
import { useAttachmentDownloadUrl } from '@/features/chat/hooks/useAttachmentDownloadUrl';
import type { AttachmentSummary } from '@/types/message';

type MessageAttachmentProps = {
  attachment: AttachmentSummary;
  isOwnMessage: boolean;
};

export function MessageAttachment({ attachment, isOwnMessage }: MessageAttachmentProps) {
  const { data, isLoading, isError } = useAttachmentDownloadUrl(attachment.id);

  if (isLoading) {
    return (
      <View style={styles.loading}>
        <ActivityIndicator color={isOwnMessage ? '#fff' : '#1A1B3A'} size="small" />
      </View>
    );
  }

  if (isError || !data) {
    return (
      <Text style={[styles.error, isOwnMessage ? styles.textOwn : styles.textOther]}>
        Attachment unavailable
      </Text>
    );
  }

  if (isImageMimeType(data.mimeType)) {
    return (
      <Image
        source={{ uri: data.downloadUrl }}
        style={styles.image}
        resizeMode="cover"
        accessibilityLabel="Image attachment"
      />
    );
  }

  const handleOpen = () => {
    void Linking.openURL(data.downloadUrl);
  };

  return (
    <Pressable
      onPress={handleOpen}
      style={[styles.fileChip, isOwnMessage ? styles.fileChipOwn : styles.fileChipOther]}
      accessibilityRole="button"
      accessibilityLabel="Open attachment"
    >
      <Text style={[styles.fileIcon, isOwnMessage ? styles.textOwn : styles.textOther]}>📎</Text>
      <View style={styles.fileMeta}>
        <Text
          style={[styles.fileLabel, isOwnMessage ? styles.textOwn : styles.textOther]}
          numberOfLines={1}
        >
          {data.mimeType}
        </Text>
        <Text style={[styles.fileSize, isOwnMessage ? styles.metaOwn : styles.metaOther]}>
          {formatFileSize(data.sizeBytes)}
        </Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  loading: {
    paddingVertical: 8,
    alignItems: 'center',
  },
  error: {
    fontSize: 13,
    fontStyle: 'italic',
  },
  image: {
    width: 220,
    height: 160,
    borderRadius: 12,
    marginBottom: 4,
  },
  fileChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    borderRadius: 12,
    paddingHorizontal: 10,
    paddingVertical: 8,
    marginBottom: 4,
  },
  fileChipOwn: {
    backgroundColor: 'rgba(255,255,255,0.12)',
  },
  fileChipOther: {
    backgroundColor: 'rgba(26,27,58,0.06)',
  },
  fileIcon: {
    fontSize: 18,
  },
  fileMeta: {
    flexShrink: 1,
  },
  fileLabel: {
    fontSize: 13,
    fontWeight: '600',
  },
  fileSize: {
    fontSize: 11,
    marginTop: 2,
  },
  textOwn: {
    color: '#fff',
  },
  textOther: {
    color: '#1A1B3A',
  },
  metaOwn: {
    color: 'rgba(255,255,255,0.75)',
  },
  metaOther: {
    color: '#666',
  },
});
