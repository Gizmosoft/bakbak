import { apiRequest } from '@/api/client';
import { API_LIMITS, API_PATHS } from '@/constants/api-paths';
import type { UserPublicResponse, UserResponse } from '@/types';

export type SearchUsersParams = {
  q?: string;
  limit?: number;
};

export function getCurrentUser(): Promise<UserResponse> {
  return apiRequest<UserResponse>(API_PATHS.users.me);
}

export function searchUsers(params: SearchUsersParams = {}): Promise<UserPublicResponse[]> {
  const limit = params.limit ?? API_LIMITS.searchDefault;
  return apiRequest<UserPublicResponse[]>(API_PATHS.users.search, {
    params: {
      q: params.q,
      limit: Math.min(Math.max(limit, 1), API_LIMITS.searchMax),
    },
  });
}
