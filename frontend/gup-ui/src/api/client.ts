import { getApiBaseUrl } from '@/config/env';
import { toApiError } from '@/api/errors';

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export type ApiRequestOptions = {
  method?: HttpMethod;
  body?: unknown;
  /** Attach Authorization header when a token is available. Default: true. */
  auth?: boolean;
  /** Query string parameters (undefined values are omitted). */
  params?: Record<string, string | number | undefined>;
};

type TokenGetter = () => string | null;

let tokenGetter: TokenGetter = () => null;

/** Called by auth layer (Phase 2) to supply the current JWT. */
export function setTokenGetter(getter: TokenGetter): void {
  tokenGetter = getter;
}

function buildUrl(path: string, params?: ApiRequestOptions['params']): string {
  const base = getApiBaseUrl();
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const url = new URL(`${base}${normalizedPath}`);

  if (params) {
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined) {
        url.searchParams.set(key, String(value));
      }
    }
  }

  return url.toString();
}

function buildHeaders(auth: boolean, hasBody: boolean): Headers {
  const headers = new Headers({
    Accept: 'application/json',
  });

  if (hasBody) {
    headers.set('Content-Type', 'application/json');
  }

  if (auth) {
    const token = tokenGetter();
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
  }

  return headers;
}

/**
 * Typed fetch wrapper for the Bakbak REST API.
 * Throws typed {@link ApiError} subclasses on non-2xx responses.
 */
export async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions = {}
): Promise<T> {
  const { method = 'GET', body, auth = true, params } = options;
  const url = buildUrl(path, params);
  const hasBody = body !== undefined;

  const response = await fetch(url, {
    method,
    headers: buildHeaders(auth, hasBody),
    body: hasBody ? JSON.stringify(body) : undefined,
  });

  if (response.ok) {
    if (response.status === 204) {
      return undefined as T;
    }
    return (await response.json()) as T;
  }

  let errorBody: unknown;
  try {
    errorBody = await response.json();
  } catch {
    errorBody = undefined;
  }

  throw toApiError(response.status, path, errorBody);
}
