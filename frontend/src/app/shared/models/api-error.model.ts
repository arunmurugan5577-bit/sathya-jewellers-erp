/**
 * The error envelope every failing endpoint returns.
 *
 * `fieldErrors` is what lets a server-side validation failure be rendered next
 * to the control that caused it, rather than as an opaque banner.
 */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  /** Stable machine-readable reason, e.g. ITEM_ALREADY_SOLD. */
  code?: string;
  message: string;
  path?: string;
  fieldErrors?: Record<string, string>;
}

/** Narrows an unknown error body to {@link ApiError}. */
export function isApiError(value: unknown): value is ApiError {
  return (
    typeof value === 'object' &&
    value !== null &&
    'status' in value &&
    'message' in value &&
    typeof (value as ApiError).message === 'string'
  );
}
