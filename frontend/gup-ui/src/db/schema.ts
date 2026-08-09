/** SQLite DDL for local-first message storage. */

export const CREATE_CONVERSATIONS_TABLE = `
CREATE TABLE IF NOT EXISTS conversations (
  id TEXT PRIMARY KEY NOT NULL,
  participant_key TEXT,
  other_user_id TEXT NOT NULL,
  other_user_display_name TEXT,
  other_user_username TEXT NOT NULL,
  created_at TEXT,
  last_message_at TEXT,
  last_message_preview TEXT
);
`;

export const CREATE_MESSAGES_TABLE = `
CREATE TABLE IF NOT EXISTS messages (
  id TEXT PRIMARY KEY NOT NULL,
  conversation_id TEXT NOT NULL,
  sender_id TEXT NOT NULL,
  content TEXT NOT NULL,
  sent_at TEXT NOT NULL,
  server_received_at TEXT,
  status TEXT NOT NULL CHECK(status IN ('SENDING','SENT','DELIVERED','FAILED')),
  client_id TEXT NOT NULL
);
`;

export const CREATE_MESSAGES_CONVERSATION_INDEX = `
CREATE INDEX IF NOT EXISTS idx_messages_conversation_sent
  ON messages(conversation_id, sent_at DESC);
`;

export const CREATE_OUTBOX_PENDING_TABLE = `
CREATE TABLE IF NOT EXISTS outbox_pending (
  id TEXT PRIMARY KEY NOT NULL,
  conversation_id TEXT NOT NULL,
  content TEXT NOT NULL,
  created_at TEXT NOT NULL,
  retry_count INTEGER NOT NULL DEFAULT 0
);
`;

export const CREATE_SIGNAL_SESSIONS_TABLE = `
CREATE TABLE IF NOT EXISTS signal_sessions (
  peer_user_id TEXT PRIMARY KEY NOT NULL,
  session_state TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
`;

export const CREATE_SIGNAL_IDENTITY_PEERS_TABLE = `
CREATE TABLE IF NOT EXISTS signal_identity_peers (
  peer_user_id TEXT PRIMARY KEY NOT NULL,
  identity_key TEXT NOT NULL,
  trusted_at TEXT NOT NULL
);
`;

export const ADD_MESSAGES_ENCRYPTION_COLUMN = `
ALTER TABLE messages ADD COLUMN encryption TEXT NOT NULL DEFAULT 'NONE';
`;
