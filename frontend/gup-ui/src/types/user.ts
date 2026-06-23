/** Public profile returned by auth endpoints and GET /api/users/me. */
export type UserResponse = {
  id: number;
  username: string;
  email: string;
  displayName: string | null;
};

/** Minimal user info for search results (no email). */
export type UserPublicResponse = {
  id: number;
  username: string;
  displayName: string | null;
};

/** Online/offline state for a user. */
export type PresenceStatus = 'ONLINE' | 'OFFLINE';

/** Presence update broadcast on WebSocket connect/disconnect and heartbeat. */
export type PresenceEvent = {
  userId: number;
  status: PresenceStatus;
  /** ISO-8601 timestamp, e.g. 2026-04-19T14:32:00Z */
  timestamp: string;
};
