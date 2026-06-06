import { useQuery } from '@tanstack/react-query';

import { listConversations } from '@/api/conversations.api';
import { queryKeys } from '@/constants/query-keys';

export function useConversations() {
  return useQuery({
    queryKey: queryKeys.conversations,
    queryFn: listConversations,
  });
}
