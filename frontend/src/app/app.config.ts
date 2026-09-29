import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { catchError, firstValueFrom, of } from 'rxjs';

import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { loadingInterceptor } from './core/interceptors/loading.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),

    provideRouter(
      routes,
      // Route parameters arrive as component inputs, so an edit screen can take
      // `id` as an input() instead of subscribing to the ActivatedRoute.
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top', anchorScrolling: 'enabled' }),
    ),

    /*
     * Interceptor order is significant and runs outermost-first:
     *
     *   loading  - counts the request, and must see every retry
     *   auth     - attaches the current access token
     *   error    - refreshes on 401 and replays, which re-enters `auth`
     */
    provideHttpClient(withInterceptors([loadingInterceptor, authInterceptor, errorInterceptor])),

    /*
     * Restore the session before the first route resolves.
     *
     * The access token lives in memory only, so a page reload always starts
     * without one. Exchanging the persisted refresh token here means a reload
     * lands the user back on the page they were on, instead of bouncing them to
     * the login screen with a valid session sitting in storage.
     *
     * A failure is not an error: it just means the session is over, and the
     * guards will route to the login page.
     */
    provideAppInitializer(() => {
      const auth = inject(AuthService);
      if (!auth.shouldRefresh()) {
        return Promise.resolve();
      }
      return firstValueFrom(auth.refresh().pipe(catchError(() => of(null))));
    }),
  ],
};
