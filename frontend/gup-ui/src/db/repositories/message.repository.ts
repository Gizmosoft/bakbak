import { executeAsync, getAllAsync, getFirstAsync } from '@/db/client';
import type { AttachmentSummary, Message, MessageEnvelope, MessageStatus } from '@/types/message';
import { envelopeToMessage } from '@/types/message';

type MessageRow = {
  id: string;
  conversation_id: string;
  sender_id: string;
  content: string;
  sent_at: string;
  server_received_at: string | null;
  status: MessageStatus;
  client_id: string;
  encryption?: string | null;
  attachment_id?: string | null;
  attachment_mime_type?: string | null;
  attachment_size_bytes?: number | null;
};

function mapAttachment(row: MessageRow): AttachmentSummary | null {
  if (!row.attachment_id || !row.attachment_mime_type || row.attachment_size_bytes == null) {
    return null;
  }
  return {
    id: row.attachment_id,
    mimeType: row.attachment_mime_type,
    sizeBytes: row.attachment_size_bytes,
  };
}

function mapRow(row: MessageRow): Message {
  return {
    id: row.id,
    conversationId: Number(row.conversation_id),
    senderId: Number(row.sender_id),
    content: row.content,
    sentAt: row.sent_at,
    serverReceivedAt: row.server_received_at,
    status: row.status,
    clientId: row.client_id,
    encryption: (row.encryption as Message['encryption']) ?? 'NONE',
    attachment: mapAttachment(row),
  };
}

export async function insertMessage(
  msg: MessageEnvelope | Message,
  status?: MessageStatus
): Promise<void> {
  const message = 'clientId' in msg ? msg : envelopeToMessage(msg, status ?? 'SENT');
  const attachment = message.attachment ?? null;

  await executeAsync(
    `INSERT OR IGNORE INTO messages (
      id, conversation_id, sender_id, content, sent_at, server_received_at,
      status, client_id, encryption, attachment_id, attachment_mime_type, attachment_size_bytes
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      message.id,
      String(message.conversationId),
      String(message.senderId),
      message.content,
      message.sentAt,
      message.serverReceivedAt,
      message.status,
      message.clientId,
      message.encryption ?? 'NONE',
      attachment?.id ?? null,
      attachment?.mimeType ?? null,
      attachment?.sizeBytes ?? null,
    ]
  );
}

export async function getMessages(
  conversationId: string,
  limit: number,
  beforeId?: string
): Promise<Message[]> {
  let rows: MessageRow[];

  if (beforeId) {
    rows = await getAllAsync<MessageRow>(
      `SELECT m.*
       FROM messages m
       WHERE m.conversation_id = ?
         AND m.sent_at < (SELECT sent_at FROM messages WHERE id = ?)
       ORDER BY m.sent_at DESC
       LIMIT ?`,
      [conversationId, beforeId, limit]
    );
  } else {
    rows = await getAllAsync<MessageRow>(
      `SELECT m.*
       FROM messages m
       WHERE m.conversation_id = ?
       ORDER BY m.sent_at DESC
       LIMIT ?`,
      [conversationId, limit]
    );
  }

  return rows.map(mapRow).reverse();
}

export async function updateMessageStatus(id: string, status: MessageStatus): Promise<void> {
  await executeAsync('UPDATE messages SET status = ? WHERE id = ?', [status, id]);
}

export async function getLastMessage(conversationId: string): Promise<Message | null> {
  const row = await getFirstAsync<MessageRow>(
    `SELECT m.*
     FROM messages m
     WHERE m.conversation_id = ?
     ORDER BY m.sent_at DESC
     LIMIT 1`,
    [conversationId]
  );
  return row ? mapRow(row) : null;
}
