import { useInfiniteQuery } from '@tanstack/react-query';

import { listMessages } from '@/api/messages.api';
import { queryKeys } from '@/constants/query-keys';
import { MESSAGE_PAGE_SIZE } from '@/websocket/cache-updates';

export function useMessages(conversationId: number) {
  return useInfiniteQuery({
    queryKey: queryKeys.messages(conversationId),
    queryFn: ({ pageParam }) =>
      listMessages(conversationId, {
        before: pageParam,
        limit: MESSAGE_PAGE_SIZE,
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => {
      if (lastPage.length < MESSAGE_PAGE_SIZE) {
        return undefined;
      }
      return lastPage[0]?.createdAt;
    },
    enabled: Number.isFinite(conversationId) && conversationId > 0,
  });
}

export function flattenMessages(
  pages: { id: number; createdAt: string }[][] | undefined
): { id: number; createdAt: string }[] {
  return pages?.flat() ?? [];
}
