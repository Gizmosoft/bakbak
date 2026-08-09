import { apiRequest } from '@/api/client';
import { API_PATHS } from '@/constants/api-paths';
import type { EncryptionType, MessageType } from '@/types/message';

export type PendingMessageResponse = {
  id: string;
  conversationId: number;
  senderId: number;
  content: string;
  sentAt: string;
  serverReceivedAt: string | null;
  type: MessageType;
  encryption?: EncryptionType;
};

export function listPendingInbox(): Promise<PendingMessageResponse[]> {
  return apiRequest<PendingMessageResponse[]>(API_PATHS.inbox.pending);
}
