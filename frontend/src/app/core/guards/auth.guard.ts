import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../auth/auth.service';

/**
 * Blocks routes that require a signed-in user.
 *
 * The attempted URL is carried to the login page so that a deep link, or a
 * session that expired mid-task, returns the user to where they were instead of
 * dumping them on the dashboard.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }

  return router.createUrlTree(['/login'], { queryParams: { redirect: state.url } });
};
