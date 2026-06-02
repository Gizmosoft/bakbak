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
