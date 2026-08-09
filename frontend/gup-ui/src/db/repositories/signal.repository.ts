import { executeAsync, getFirstAsync } from '@/db/client';

export async function getSessionState(peerUserId: number): Promise<string | null> {
  const row = await getFirstAsync<{ session_state: string }>(
    'SELECT session_state FROM signal_sessions WHERE peer_user_id = ?',
    [String(peerUserId)]
  );
  return row?.session_state ?? null;
}

export async function saveSessionState(peerUserId: number, sessionState: string): Promise<void> {
  const updatedAt = new Date().toISOString();
  await executeAsync(
    `INSERT INTO signal_sessions (peer_user_id, session_state, updated_at)
     VALUES (?, ?, ?)
     ON CONFLICT(peer_user_id) DO UPDATE SET
       session_state = excluded.session_state,
       updated_at = excluded.updated_at`,
    [String(peerUserId), sessionState, updatedAt]
  );
}

export async function deleteSession(peerUserId: number): Promise<void> {
  await executeAsync('DELETE FROM signal_sessions WHERE peer_user_id = ?', [String(peerUserId)]);
}

export async function getTrustedIdentity(peerUserId: number): Promise<string | null> {
  const row = await getFirstAsync<{ identity_key: string }>(
    'SELECT identity_key FROM signal_identity_peers WHERE peer_user_id = ?',
    [String(peerUserId)]
  );
  return row?.identity_key ?? null;
}

export async function trustIdentity(peerUserId: number, identityKeyBase64: string): Promise<void> {
  const now = new Date().toISOString();
  await executeAsync(
    `INSERT INTO signal_identity_peers (peer_user_id, identity_key, trusted_at)
     VALUES (?, ?, ?)
     ON CONFLICT(peer_user_id) DO UPDATE SET
       identity_key = excluded.identity_key,
       trusted_at = excluded.trusted_at`,
    [String(peerUserId), identityKeyBase64, now]
  );
}

export async function clearTrustedIdentity(peerUserId: number): Promise<void> {
  await executeAsync('DELETE FROM signal_identity_peers WHERE peer_user_id = ?', [
    String(peerUserId),
  ]);
}
