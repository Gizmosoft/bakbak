import type { UserResponse } from './user';

/** Response from POST /api/auth/register and POST /api/auth/login. */
export type AuthResponse = {
  token: string;
  user: UserResponse;
};

/** Request body for POST /api/auth/register. dateOfBirth is ISO date (YYYY-MM-DD). */
export type RegisterRequest = {
  username: string;
  email: string;
  password: string;
  displayName?: string;
  dateOfBirth: string;
};

/** Request body for POST /api/auth/login. */
export type LoginRequest = {
  email: string;
  password: string;
};
