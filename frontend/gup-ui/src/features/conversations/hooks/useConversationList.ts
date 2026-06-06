import { useMemo } from 'react';

import { mergeConversationsWithDrafts } from '@/features/conversations/lib/merge-conversations-with-drafts';
import { useConversations } from '@/features/conversations/hooks/useConversations';
import { useChatDrafts } from '@/providers/ChatDraftProvider';

export function useConversationList() {
  const conversationsQuery = useConversations();
  const { drafts } = useChatDrafts();

  const rows = useMemo(
    () => mergeConversationsWithDrafts(conversationsQuery.data ?? [], drafts),
    [conversationsQuery.data, drafts]
  );

  return {
    ...conversationsQuery,
    rows,
  };
}
