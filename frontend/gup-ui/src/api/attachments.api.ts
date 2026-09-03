import { apiRequest } from '@/api/client';
import { API_PATHS } from '@/constants/api-paths';

export type AttachmentSummary = {
  id: string;
  mimeType: string;
  sizeBytes: number;
};

export type AttachmentIntentRequest = {
  conversationId: number;
  mimeType: string;
  sizeBytes: number;
  fileName?: string;
};

export type AttachmentIntentResponse = {
  attachmentId: string;
  uploadUrl: string;
};

export type AttachmentDownloadResponse = {
  attachmentId: string;
  downloadUrl: string;
  mimeType: string;
  sizeBytes: number;
};

export async function createAttachmentIntent(
  request: AttachmentIntentRequest
): Promise<AttachmentIntentResponse> {
  return apiRequest<AttachmentIntentResponse>(API_PATHS.attachments.intent, {
    method: 'POST',
    body: request,
  });
}

export async function getAttachmentDownloadUrl(
  attachmentId: string
): Promise<AttachmentDownloadResponse> {
  return apiRequest<AttachmentDownloadResponse>(API_PATHS.attachments.downloadUrl(attachmentId));
}

export async function uploadToPresignedUrl(
  uploadUrl: string,
  fileUri: string,
  mimeType: string
): Promise<void> {
  const fileResponse = await fetch(fileUri);
  const blob = await fileResponse.blob();

  const response = await fetch(uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': mimeType },
    body: blob,
  });

  if (!response.ok) {
    throw new Error(`Upload failed (${response.status})`);
  }
}
