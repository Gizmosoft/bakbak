import type { SQLiteDatabase } from 'expo-sqlite';

import {
  CREATE_CONVERSATIONS_TABLE,
  CREATE_MESSAGES_CONVERSATION_INDEX,
  CREATE_MESSAGES_TABLE,
  CREATE_OUTBOX_PENDING_TABLE,
} from './schema';

const MIGRATIONS: Record<number, string[]> = {
  1: [
    CREATE_CONVERSATIONS_TABLE,
    CREATE_MESSAGES_TABLE,
    CREATE_MESSAGES_CONVERSATION_INDEX,
    CREATE_OUTBOX_PENDING_TABLE,
  ],
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
