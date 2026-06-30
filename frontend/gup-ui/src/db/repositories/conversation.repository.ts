import { executeAsync, getAllAsync, getFirstAsync } from '@/db/client';
import type {
  ConversationRecord,
  ConversationWithLastMessage,
} from '@/types/conversation';

type ConversationRow = {
  id: string;
  participant_key: string | null;
  other_user_id: string;
  other_user_display_name: string | null;
  other_user_username: string;
  created_at: string | null;
  last_message_at: string | null;
  last_message_preview: string | null;
};

function mapRow(row: ConversationRow): ConversationWithLastMessage {
  return {
    id: row.id,
    participantKey: row.participant_key,
    otherUserId: row.other_user_id,
    otherUserDisplayName: row.other_user_display_name,
    otherUserUsername: row.other_user_username,
    createdAt: row.created_at,
    lastMessageAt: row.last_message_at,
    lastMessagePreview: row.last_message_preview,
    otherUserPresence: 'UNKNOWN',
  };
}

export async function upsertConversation(conv: ConversationRecord): Promise<void> {
  await executeAsync(
    `INSERT INTO conversations (
      id, participant_key, other_user_id, other_user_display_name, other_user_username,
      created_at, last_message_at, last_message_preview
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(id) DO UPDATE SET
      participant_key = COALESCE(excluded.participant_key, conversations.participant_key),
      other_user_id = excluded.other_user_id,
      other_user_display_name = excluded.other_user_display_name,
      other_user_username = excluded.other_user_username,
      created_at = COALESCE(conversations.created_at, excluded.created_at),
      last_message_at = COALESCE(excluded.last_message_at, conversations.last_message_at),
      last_message_preview = COALESCE(excluded.last_message_preview, conversations.last_message_preview)`,
    [
      conv.id,
      conv.participantKey,
      conv.otherUserId,
      conv.otherUserDisplayName,
      conv.otherUserUsername,
      conv.createdAt,
      conv.lastMessageAt,
      conv.lastMessagePreview,
    ]
  );
}

export async function listConversations(
  _userId: string
): Promise<ConversationWithLastMessage[]> {
  const rows = await getAllAsync<ConversationRow>(
    `SELECT c.*
     FROM conversations c
     ORDER BY COALESCE(c.last_message_at, c.created_at, c.id) DESC`
  );
  return rows.map(mapRow);
}

export async function getConversation(id: string): Promise<ConversationRecord | null> {
  const row = await getFirstAsync<ConversationRow>(
    'SELECT * FROM conversations WHERE id = ?',
    [id]
  );
  return row ? mapRow(row) : null;
}

export async function updateLastMessage(
  conversationId: string,
  preview: string,
  at: string
): Promise<void> {
  await executeAsync(
    `UPDATE conversations
     SET last_message_preview = ?, last_message_at = ?
     WHERE id = ?`,
    [preview, at, conversationId]
  );
}
