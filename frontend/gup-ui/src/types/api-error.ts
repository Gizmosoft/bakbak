/** Standard error body returned by the backend on non-2xx responses. */
export type ApiErrorResponse = {
  /** ISO-8601 timestamp */
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
};
