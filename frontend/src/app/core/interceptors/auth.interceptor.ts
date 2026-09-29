import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { environment } from '../../../environments/environment';
import { TokenStorageService } from '../auth/token-storage.service';

/** Endpoints that must never carry an Authorization header. */
const ANONYMOUS_PATHS = ['/auth/login', '/auth/refresh'];

/**
 * Attaches the access token to API calls.
 *
 * Two guards on what gets a header: only requests to our own API, and never the
 * login or refresh calls. Sending a stale token to `/auth/refresh` would make it
 * fail for the wrong reason and turn an expired session into a confusing error.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const storage = inject(TokenStorageService);

  const isApiRequest = request.url.startsWith(environment.apiUrl);
  const isAnonymousEndpoint = ANONYMOUS_PATHS.some((path) => request.url.includes(path));
  const token = storage.getAccessToken();

  if (!isApiRequest || isAnonymousEndpoint || !token) {
    return next(request);
  }

  return next(
    request.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    }),
  );
};
