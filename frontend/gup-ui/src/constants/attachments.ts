/** Mirrors backend {@code bakbak.storage} limits. */
export const ATTACHMENT = {
  maxSizeBytes: 25 * 1024 * 1024,
  allowedMimeTypes: [
    'image/jpeg',
    'image/png',
    'image/gif',
    'image/webp',
    'video/mp4',
    'video/quicktime',
    'audio/mpeg',
    'audio/mp4',
    'audio/ogg',
    'application/pdf',
  ] as const,
} as const;

export type AllowedMimeType = (typeof ATTACHMENT.allowedMimeTypes)[number];

export function isAllowedMimeType(mimeType: string): mimeType is AllowedMimeType {
  return (ATTACHMENT.allowedMimeTypes as readonly string[]).includes(mimeType);
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function isImageMimeType(mimeType: string): boolean {
  return mimeType.startsWith('image/');
}
