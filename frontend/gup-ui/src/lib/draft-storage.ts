import * as SecureStore from 'expo-secure-store';

import type { UserPublicResponse } from '@/types/user';

const DRAFTS_KEY = 'gup.chat.drafts';

export type StoredChatDraft = {
  conversationId: number;
  otherUser: UserPublicResponse;
  draftText: string;
};

export async function loadStoredDrafts(): Promise<StoredChatDraft[]> {
  const raw = await SecureStore.getItemAsync(DRAFTS_KEY);
  if (!raw) {
    return [];
  }

  try {
    const parsed = JSON.parse(raw) as StoredChatDraft[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export async function saveStoredDrafts(drafts: StoredChatDraft[]): Promise<void> {
  if (drafts.length === 0) {
    await SecureStore.deleteItemAsync(DRAFTS_KEY);
    return;
  }

  await SecureStore.setItemAsync(DRAFTS_KEY, JSON.stringify(drafts));
}

export async function clearStoredDrafts(): Promise<void> {
  await SecureStore.deleteItemAsync(DRAFTS_KEY);
}
