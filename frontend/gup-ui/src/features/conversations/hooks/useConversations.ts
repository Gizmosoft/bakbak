import { useQuery } from '@tanstack/react-query';

import { getConversationPresence } from '@/api/presence.api';
import * as conversationRepository from '@/db/repositories/conversation.repository';
import { resyncConversationsFromServer } from '@/db/sync/bootstrap';
import { queryKeys } from '@/constants/query-keys';
import { useAuth } from '@/providers/AuthProvider';
import { useDatabaseContext } from '@/providers/DatabaseProvider';
import {
  conversationRecordToResponse,
  type ConversationResponse,
} from '@/types/conversation';
import type { PresenceStatus } from '@/types/user';

async function enrichWithPresence(
  conversations: ConversationResponse[]
): Promise<ConversationResponse[]> {
  return Promise.all(
    conversations.map(async (conversation) => {
      try {
        const presenceMap = await getConversationPresence(conversation.conversationId);
        const status =
          presenceMap[String(conversation.otherUser.id)] ??
          presenceMap[conversation.otherUser.id] ??
          'UNKNOWN';
        return {
          ...conversation,
          otherUserPresence: status as PresenceStatus | 'UNKNOWN',
        };
      } catch {
        return conversation;
      }
    })
  );
}

export function useConversations() {
  const { user } = useAuth();
  const { isReady } = useDatabaseContext();

  return useQuery({
    queryKey: queryKeys.conversations,
    queryFn: async () => {
      await resyncConversationsFromServer();
      const localRows = await conversationRepository.listConversations(String(user!.id));
      const conversations = localRows.map(conversationRecordToResponse);
      return enrichWithPresence(conversations);
    },
    enabled: isReady && user !== null,
  });
}
