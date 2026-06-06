import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';

import {
  clearStoredDrafts,
  loadStoredDrafts,
  saveStoredDrafts,
  type StoredChatDraft,
} from '@/lib/draft-storage';
import { useAuth } from '@/providers/AuthProvider';
import type { ConversationResponse } from '@/types/conversation';
import type { UserPublicResponse } from '@/types/user';

type ChatDraftContextValue = {
  drafts: StoredChatDraft[];
  registerConversation: (conversation: ConversationResponse) => void;
  setDraftText: (conversationId: number, otherUser: UserPublicResponse, text: string) => void;
  getDraftText: (conversationId: number) => string;
  getOtherUser: (conversationId: number) => UserPublicResponse | null;
  clearDraft: (conversationId: number) => void;
  clearAllDrafts: () => Promise<void>;
};

const ChatDraftContext = createContext<ChatDraftContextValue | null>(null);

function upsertDraft(
  drafts: StoredChatDraft[],
  conversationId: number,
  otherUser: UserPublicResponse,
  draftText: string
): StoredChatDraft[] {
  const trimmed = draftText.trim();
  const without = drafts.filter((draft) => draft.conversationId !== conversationId);

  if (trimmed.length === 0) {
    return without;
  }

  return [...without, { conversationId, otherUser, draftText }];
}

export function ChatDraftProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth();
  const [drafts, setDrafts] = useState<StoredChatDraft[]>([]);

  useEffect(() => {
    if (!isAuthenticated) {
      setDrafts([]);
      return;
    }

    void loadStoredDrafts().then(setDrafts);
  }, [isAuthenticated]);

  const persistDrafts = useCallback(async (nextDrafts: StoredChatDraft[]) => {
    setDrafts(nextDrafts);
    await saveStoredDrafts(nextDrafts);
  }, []);

  const registerConversation = useCallback((conversation: ConversationResponse) => {
    setDrafts((current) => {
      if (current.some((draft) => draft.conversationId === conversation.conversationId)) {
        return current;
      }

      return [
        ...current,
        {
          conversationId: conversation.conversationId,
          otherUser: conversation.otherUser,
          draftText: '',
        },
      ];
    });
  }, []);

  const setDraftText = useCallback(
    (conversationId: number, otherUser: UserPublicResponse, text: string) => {
      setDrafts((current) => {
        const next = upsertDraft(current, conversationId, otherUser, text);
        void saveStoredDrafts(next);
        return next;
      });
    },
    []
  );

  const getDraftText = useCallback(
    (conversationId: number) =>
      drafts.find((draft) => draft.conversationId === conversationId)?.draftText ?? '',
    [drafts]
  );

  const getOtherUser = useCallback(
    (conversationId: number) =>
      drafts.find((draft) => draft.conversationId === conversationId)?.otherUser ?? null,
    [drafts]
  );

  const clearDraft = useCallback(
    (conversationId: number) => {
      setDrafts((current) => {
        const next = current.filter((draft) => draft.conversationId !== conversationId);
        void saveStoredDrafts(next);
        return next;
      });
    },
    []
  );

  const clearAllDrafts = useCallback(async () => {
    setDrafts([]);
    await clearStoredDrafts();
  }, []);

  const value = useMemo<ChatDraftContextValue>(
    () => ({
      drafts,
      registerConversation,
      setDraftText,
      getDraftText,
      getOtherUser,
      clearDraft,
      clearAllDrafts,
    }),
    [
      drafts,
      registerConversation,
      setDraftText,
      getDraftText,
      getOtherUser,
      clearDraft,
      clearAllDrafts,
    ]
  );

  return <ChatDraftContext.Provider value={value}>{children}</ChatDraftContext.Provider>;
}

export function useChatDrafts(): ChatDraftContextValue {
  const context = useContext(ChatDraftContext);
  if (!context) {
    throw new Error('useChatDrafts must be used within ChatDraftProvider');
  }
  return context;
}
