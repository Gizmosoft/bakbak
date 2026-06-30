import * as conversationRepository from '@/db/repositories/conversation.repository';
import type { ConversationResponse } from '@/types/conversation';
import { conversationResponseToRecord } from '@/types/conversation';

export async function syncConversationsFromServer(
  conversations: ConversationResponse[]
): Promise<void> {
  for (const conversation of conversations) {
    await conversationRepository.upsertConversation(conversationResponseToRecord(conversation));
  }
}

export async function upsertConversationFromResponse(
  conversation: ConversationResponse
): Promise<void> {
  await conversationRepository.upsertConversation(conversationResponseToRecord(conversation));
}
