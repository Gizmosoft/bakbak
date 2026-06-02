import { apiRequest } from '@/api/client';
import { API_PATHS } from '@/constants/api-paths';
import type { AuthResponse, LoginRequest, RegisterRequest } from '@/types';

export function register(request: RegisterRequest): Promise<AuthResponse> {
  return apiRequest<AuthResponse>(API_PATHS.auth.register, {
    method: 'POST',
    body: request,
    auth: false,
  });
}

export function login(request: LoginRequest): Promise<AuthResponse> {
  return apiRequest<AuthResponse>(API_PATHS.auth.login, {
    method: 'POST',
    body: request,
    auth: false,
  });
}
