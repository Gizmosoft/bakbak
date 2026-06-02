import { apiRequest } from '@/api/client';
import { API_LIMITS, API_PATHS } from '@/constants/api-paths';
import type { MessageResponse } from '@/types';

export type ListMessagesParams = {
  before?: string;
  limit?: number;
};

export function listMessages(
  conversationId: number,
  params: ListMessagesParams = {}
): Promise<MessageResponse[]> {
  const limit = params.limit ?? API_LIMITS.messagesDefault;
  return apiRequest<MessageResponse[]>(API_PATHS.conversations.messages(conversationId), {
    params: {
      before: params.before,
      limit: Math.min(Math.max(limit, 1), API_LIMITS.messagesMax),
    },
  });
}
