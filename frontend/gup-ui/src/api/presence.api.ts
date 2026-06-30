import { apiRequest } from '@/api/client';
import { API_PATHS } from '@/constants/api-paths';
import type { PresenceStatus } from '@/types/user';

export function getConversationPresence(
  conversationId: number
): Promise<Record<string, PresenceStatus>> {
  return apiRequest<Record<string, PresenceStatus>>(
    API_PATHS.conversations.presence(conversationId)
  );
}
