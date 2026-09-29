/** Mirrors the backend {@code PageResponse}. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

/** An empty page, used as the initial state so tables never render `undefined`. */
export function emptyPage<T>(size = 20): Page<T> {
  return {
    content: [],
    page: 0,
    size,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  };
}

/** Server-side paging, sorting and free-text parameters shared by every list. */
export interface PageQuery {
  page: number;
  size: number;
  sort?: string;
  direction?: 'asc' | 'desc';
}

export const DEFAULT_PAGE_SIZE = 20;
export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];
