import { apiRequest } from '@/api/client';
import { API_PATHS } from '@/constants/api-paths';
import type { ConversationResponse, CreateConversationRequest } from '@/types';

export function listConversations(): Promise<ConversationResponse[]> {
  return apiRequest<ConversationResponse[]>(API_PATHS.conversations.list);
}

export function createOrFetchConversation(
  request: CreateConversationRequest
): Promise<ConversationResponse> {
  return apiRequest<ConversationResponse>(API_PATHS.conversations.list, {
    method: 'POST',
    body: request,
  });
}
