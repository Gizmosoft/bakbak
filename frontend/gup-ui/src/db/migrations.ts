import type { SQLiteDatabase } from 'expo-sqlite';

import {
  ADD_MESSAGES_ATTACHMENT_COLUMNS,
  ADD_MESSAGES_ENCRYPTION_COLUMN,
  CREATE_CONVERSATIONS_TABLE,
  CREATE_MESSAGES_CONVERSATION_INDEX,
  CREATE_MESSAGES_TABLE,
  CREATE_OUTBOX_PENDING_TABLE,
  CREATE_SIGNAL_IDENTITY_PEERS_TABLE,
  CREATE_SIGNAL_SESSIONS_TABLE,
} from './schema';

const MIGRATIONS: Record<number, string[]> = {
  1: [
    CREATE_CONVERSATIONS_TABLE,
    CREATE_MESSAGES_TABLE,
    CREATE_MESSAGES_CONVERSATION_INDEX,
    CREATE_OUTBOX_PENDING_TABLE,
  ],
  2: [
    CREATE_SIGNAL_SESSIONS_TABLE,
    CREATE_SIGNAL_IDENTITY_PEERS_TABLE,
    ADD_MESSAGES_ENCRYPTION_COLUMN,
  ],
  3: [ADD_MESSAGES_ATTACHMENT_COLUMNS],
};

export async function runMigrations(db: SQLiteDatabase): Promise<void> {
  const versionRow = await db.getFirstAsync<{ user_version: number }>('PRAGMA user_version');
  let version = versionRow?.user_version ?? 0;

  while (MIGRATIONS[version + 1]) {
    const nextVersion = version + 1;
    for (const statement of MIGRATIONS[nextVersion]) {
      await db.execAsync(statement);
    }
    await db.execAsync(`PRAGMA user_version = ${nextVersion}`);
    version = nextVersion;
  }
}
