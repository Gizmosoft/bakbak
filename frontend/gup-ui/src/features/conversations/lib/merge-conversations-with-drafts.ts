import type { StoredChatDraft } from '@/lib/draft-storage';
import type { ConversationResponse } from '@/types/conversation';

export type ConversationListRow = ConversationResponse & {
  draftPreview?: string | null;
};

export function mergeConversationsWithDrafts(
  conversations: ConversationResponse[],
  drafts: StoredChatDraft[]
): ConversationListRow[] {
  const activeDrafts = drafts.filter((draft) => draft.draftText.trim().length > 0);
  const draftByConversationId = new Map(
    activeDrafts.map((draft) => [draft.conversationId, draft.draftText.trim()])
  );

  const mergedServerRows: ConversationListRow[] = conversations.map((conversation) => ({
    ...conversation,
    draftPreview: draftByConversationId.get(conversation.conversationId) ?? null,
  }));

  const serverIds = new Set(conversations.map((conversation) => conversation.conversationId));
  const draftOnlyRows: ConversationListRow[] = activeDrafts
    .filter((draft) => !serverIds.has(draft.conversationId))
    .map((draft) => ({
      conversationId: draft.conversationId,
      otherUser: draft.otherUser,
      lastMessage: null,
      lastMessageAt: null,
      draftPreview: draft.draftText.trim(),
    }));

  return [...draftOnlyRows, ...mergedServerRows];
}
