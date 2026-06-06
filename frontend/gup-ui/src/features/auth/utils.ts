import { isApiError } from '@/api/errors';
import type { RegisterRequest } from '@/types';
import type { RegisterFormValues } from './schemas';

export function formatAuthError(error: unknown): string {
  if (isApiError(error)) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return 'Something went wrong. Please try again.';
}

export function toRegisterRequest(values: RegisterFormValues): RegisterRequest {
  const displayName = values.displayName?.trim();
  return {
    username: values.username.trim(),
    email: values.email.trim(),
    password: values.password,
    dateOfBirth: values.dateOfBirth,
    ...(displayName ? { displayName } : {}),
  };
}
