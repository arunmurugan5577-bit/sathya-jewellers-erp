import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

/** Query parameter values the API accepts. Null and undefined are dropped. */
export type QueryParams = Record<string, string | number | boolean | null | undefined>;

/**
 * Thin wrapper over HttpClient.
 *
 * Its only jobs are prefixing the API base URL and dropping empty query
 * parameters - sending `?search=` or `?active=null` would make the backend
 * filter on an empty string rather than not filter at all.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  get<T>(path: string, params?: QueryParams, context?: HttpContext): Observable<T> {
    return this.http.get<T>(this.url(path), { params: toHttpParams(params), context });
  }

  post<T>(path: string, body: unknown, context?: HttpContext): Observable<T> {
    return this.http.post<T>(this.url(path), body, { context });
  }

  put<T>(path: string, body: unknown, context?: HttpContext): Observable<T> {
    return this.http.put<T>(this.url(path), body, { context });
  }

  patch<T>(path: string, body: unknown, context?: HttpContext): Observable<T> {
    return this.http.patch<T>(this.url(path), body, { context });
  }

  delete<T>(path: string, context?: HttpContext): Observable<T> {
    return this.http.delete<T>(this.url(path), { context });
  }

  private url(path: string): string {
    return `${environment.apiUrl}${path.startsWith('/') ? path : `/${path}`}`;
  }
}

function toHttpParams(params?: QueryParams): HttpParams {
  let httpParams = new HttpParams();
  if (!params) {
    return httpParams;
  }
  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined || value === '') {
      continue;
    }
    httpParams = httpParams.set(key, String(value));
  }
  return httpParams;
}
