import type { ApiErrorResponse } from '@/types/api-error';

export class ApiError extends Error {
  readonly status: number;
  readonly body: ApiErrorResponse;

  constructor(status: number, body: ApiErrorResponse) {
    super(body.message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

export class UnauthorizedError extends ApiError {
  constructor(body: ApiErrorResponse) {
    super(401, body);
    this.name = 'UnauthorizedError';
  }
}

export class ForbiddenError extends ApiError {
  constructor(body: ApiErrorResponse) {
    super(403, body);
    this.name = 'ForbiddenError';
  }
}

export class NotFoundError extends ApiError {
  constructor(body: ApiErrorResponse) {
    super(404, body);
    this.name = 'NotFoundError';
  }
}

export class ConflictError extends ApiError {
  constructor(body: ApiErrorResponse) {
    super(409, body);
    this.name = 'ConflictError';
  }
}

export class ValidationError extends ApiError {
  constructor(body: ApiErrorResponse) {
    super(400, body);
    this.name = 'ValidationError';
  }
}

function fallbackError(status: number, path: string, message: string): ApiErrorResponse {
  return {
    timestamp: new Date().toISOString(),
    status,
    error: 'Error',
    message,
    path,
  };
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

export function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const body = value as Record<string, unknown>;
  return (
    typeof body.status === 'number' &&
    typeof body.message === 'string' &&
    typeof body.error === 'string' &&
    typeof body.path === 'string'
  );
}

export function toApiError(status: number, path: string, body: unknown): ApiError {
  const errorBody = isApiErrorResponse(body)
    ? body
    : fallbackError(status, path, 'Request failed');

  switch (status) {
    case 400:
      return new ValidationError(errorBody);
    case 401:
      return new UnauthorizedError(errorBody);
    case 403:
      return new ForbiddenError(errorBody);
    case 404:
      return new NotFoundError(errorBody);
    case 409:
      return new ConflictError(errorBody);
    default:
      return new ApiError(status, errorBody);
  }
}
