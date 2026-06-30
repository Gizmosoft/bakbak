import { executeAsync, getAllAsync } from '@/db/client';
import type { OutboxPending } from '@/types/message';

type OutboxRow = {
  id: string;
  conversation_id: string;
  content: string;
  created_at: string;
  retry_count: number;
};

function mapRow(row: OutboxRow): OutboxPending {
  return {
    id: row.id,
    conversationId: row.conversation_id,
    content: row.content,
    createdAt: row.created_at,
    retryCount: row.retry_count,
  };
}

export async function enqueue(item: OutboxPending): Promise<void> {
  await executeAsync(
    `INSERT INTO outbox_pending (id, conversation_id, content, created_at, retry_count)
     VALUES (?, ?, ?, ?, ?)
     ON CONFLICT(id) DO UPDATE SET
       content = excluded.content,
       created_at = excluded.created_at`,
    [item.id, item.conversationId, item.content, item.createdAt, item.retryCount]
  );
}

export async function dequeue(id: string): Promise<void> {
  await executeAsync('DELETE FROM outbox_pending WHERE id = ?', [id]);
}

export async function getPending(): Promise<OutboxPending[]> {
  const rows = await getAllAsync<OutboxRow>(
    'SELECT * FROM outbox_pending ORDER BY created_at ASC'
  );
  return rows.map(mapRow);
}

export async function incrementRetry(id: string): Promise<void> {
  await executeAsync(
    'UPDATE outbox_pending SET retry_count = retry_count + 1 WHERE id = ?',
    [id]
  );
}

export async function purgeFailed(maxRetries: number): Promise<void> {
  await executeAsync('DELETE FROM outbox_pending WHERE retry_count >= ?', [maxRetries]);
}
