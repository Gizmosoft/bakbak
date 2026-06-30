import { useInfiniteQuery } from '@tanstack/react-query';

import * as messageRepository from '@/db/repositories/message.repository';
import { queryKeys } from '@/constants/query-keys';
import { useDatabaseContext } from '@/providers/DatabaseProvider';
import type { MessageResponse } from '@/types/message';
import { messageToResponse } from '@/types/message';
import { MESSAGE_PAGE_SIZE } from '@/websocket/cache-updates';

export function useMessages(conversationId: number) {
  const { isReady } = useDatabaseContext();

  return useInfiniteQuery({
    queryKey: queryKeys.messages(conversationId),
    queryFn: async ({ pageParam }) => {
      const rows = await messageRepository.getMessages(
        String(conversationId),
        MESSAGE_PAGE_SIZE,
        pageParam
      );
      return rows.map(messageToResponse);
    },
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => {
      if (lastPage.length < MESSAGE_PAGE_SIZE) {
        return undefined;
      }
      return lastPage[0]?.id;
    },
    enabled: isReady && Number.isFinite(conversationId) && conversationId > 0,
  });
}

export function flattenMessages(
  pages: MessageResponse[][] | undefined
): MessageResponse[] {
  return pages?.flat() ?? [];
}
