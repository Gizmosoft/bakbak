import { useQuery } from '@tanstack/react-query';

import { getConversationPresence } from '@/api/presence.api';
import type { PresenceStatus } from '@/types/user';

export type ParticipantPresence = {
  userId: number;
  status: PresenceStatus;
};

export function usePresence(conversationId: number) {
  return useQuery({
    queryKey: ['presence', conversationId] as const,
    queryFn: async (): Promise<ParticipantPresence[]> => {
      const presenceMap = await getConversationPresence(conversationId);
      return Object.entries(presenceMap).map(([userId, status]) => ({
        userId: Number(userId),
        status,
      }));
    },
    enabled: Number.isFinite(conversationId) && conversationId > 0,
    refetchInterval: 60_000,
  });
}
