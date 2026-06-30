import type { SQLiteBindParams, SQLiteDatabase } from 'expo-sqlite';
import * as SQLite from 'expo-sqlite';

import { runMigrations } from './migrations';

let dbInstance: SQLiteDatabase | null = null;

export async function openDatabase(): Promise<SQLiteDatabase> {
  if (dbInstance) {
    return dbInstance;
  }

  const db = await SQLite.openDatabaseAsync('gup.db');
  await db.execAsync('PRAGMA journal_mode = WAL;');
  await runMigrations(db);
  dbInstance = db;
  return db;
}

export function getDatabase(): SQLiteDatabase {
  if (!dbInstance) {
    throw new Error('Database is not open — wrap the app in DatabaseProvider');
  }
  return dbInstance;
}

export async function executeAsync(
  sql: string,
  params: SQLiteBindParams = []
): Promise<void> {
  await getDatabase().runAsync(sql, params);
}

export async function getAllAsync<T>(
  sql: string,
  params: SQLiteBindParams = []
): Promise<T[]> {
  return getDatabase().getAllAsync<T>(sql, params);
}

export async function getFirstAsync<T>(
  sql: string,
  params: SQLiteBindParams = []
): Promise<T | null> {
  return getDatabase().getFirstAsync<T>(sql, params);
}
