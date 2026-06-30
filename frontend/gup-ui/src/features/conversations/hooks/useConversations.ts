import { useQuery } from '@tanstack/react-query';

import { listConversations } from '@/api/conversations.api';
import * as conversationRepository from '@/db/repositories/conversation.repository';
import { syncConversationsFromServer } from '@/db/sync/conversation-sync';
import { queryKeys } from '@/constants/query-keys';
import { useAuth } from '@/providers/AuthProvider';
import { useDatabaseContext } from '@/providers/DatabaseProvider';
import { conversationRecordToResponse } from '@/types/conversation';

export function useConversations() {
  const { user } = useAuth();
  const { isReady } = useDatabaseContext();

  return useQuery({
    queryKey: queryKeys.conversations,
    queryFn: async () => {
      const serverConversations = await listConversations();
      await syncConversationsFromServer(serverConversations);
      const localRows = await conversationRepository.listConversations(String(user!.id));
      return localRows.map(conversationRecordToResponse);
    },
    enabled: isReady && user !== null,
  });
}
