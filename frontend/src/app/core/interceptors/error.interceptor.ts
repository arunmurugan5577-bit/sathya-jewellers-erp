import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { TokenStorageService } from '../auth/token-storage.service';
import { NotificationService } from '../services/notification.service';
import { ApiError, isApiError } from '../../shared/models/api-error.model';

/**
 * Opt out of the automatic toast for one request.
 *
 * Forms set this because they render field-level errors inline; a banner saying
 * "Validation failed" on top of that is noise.
 */
export const SUPPRESS_ERROR_TOAST = new HttpContextToken<boolean>(() => false);

/** Marks a request as already retried, so a refresh loop cannot form. */
const RETRIED = new HttpContextToken<boolean>(() => false);

/**
 * Turns HTTP failures into something the user can act on.
 *
 * The important behaviour is the 401 path: a single expired access token is
 * refreshed and the original request replayed, so a user working through a long
 * form never loses their work to a token that quietly aged out. If the refresh
 * itself fails the session is over, and they are returned to the login screen
 * with the URL they were on so they land back where they were.
 */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const storage = inject(TokenStorageService);
  const router = inject(Router);
  const notifications = inject(NotificationService);

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      const isApiRequest = request.url.startsWith(environment.apiUrl);
      const isRefreshCall = request.url.includes('/auth/refresh');
      const isLoginCall = request.url.includes('/auth/login');

      if (!isApiRequest) {
        return throwError(() => error);
      }

      // --- 401: try exactly one refresh, then replay the original request ---
      if (
        error.status === 401 &&
        !isRefreshCall &&
        !isLoginCall &&
        !request.context.get(RETRIED) &&
        storage.getRefreshToken()
      ) {
        return auth.refresh().pipe(
          switchMap(() =>
            next(
              request.clone({
                context: request.context.set(RETRIED, true),
                setHeaders: { Authorization: `Bearer ${storage.getAccessToken()}` },
              }),
            ),
          ),
          catchError(() => {
            endSession('Your session has expired. Please sign in again.');
            return throwError(() => error);
          }),
        );
      }

      if (error.status === 401 && !isLoginCall) {
        endSession('Your session has expired. Please sign in again.');
        return throwError(() => error);
      }

      if (!request.context.get(SUPPRESS_ERROR_TOAST)) {
        notifications.error(describe(error));
      }

      return throwError(() => error);
    }),
  );

  function endSession(message: string): void {
    auth.clearSession();
    notifications.error(message);
    const redirect = router.url.startsWith('/login') ? undefined : router.url;
    void router.navigate(['/login'], { queryParams: redirect ? { redirect } : undefined });
  }
};

/**
 * A message worth showing.
 *
 * The server's message is preferred whenever there is one - it knows why the
 * request failed. The fallbacks exist for the cases where there is no body at
 * all: a dead network, a proxy timeout, a 502.
 */
function describe(error: HttpErrorResponse): string {
  if (error.status === 0) {
    return 'Cannot reach the server. Check your connection and try again.';
  }

  const body: ApiError | unknown = error.error;
  if (isApiError(body) && body.message) {
    return body.message;
  }

  switch (error.status) {
    case 403:
      return 'You do not have permission to perform this action.';
    case 404:
      return 'The requested record was not found.';
    case 409:
      return 'This operation conflicts with existing data.';
    case 502:
    case 503:
    case 504:
      return 'The server is unavailable. Please try again in a moment.';
    default:
      return 'Something went wrong. Please try again.';
  }
}
