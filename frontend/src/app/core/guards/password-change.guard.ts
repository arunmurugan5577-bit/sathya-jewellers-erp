import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../auth/auth.service';

/**
 * Forces a password change before anything else.
 *
 * A bootstrap administrator's password comes from an environment variable, and a
 * reset password is chosen by someone other than its owner. Neither should stay
 * in use, so until the user picks their own, every route redirects to the change
 * screen.
 */
export const passwordChangeGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.mustChangePassword() || state.url.startsWith('/change-password')) {
    return true;
  }

  return router.createUrlTree(['/change-password']);
};
