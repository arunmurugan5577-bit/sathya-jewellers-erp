import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { PermissionCode } from '../auth/permissions';
import { NotificationService } from '../services/notification.service';

/**
 * Guards a route behind one or more permissions.
 *
 * Holding *any* of the listed permissions is enough - a screen that needs
 * VIEW to open and CREATE to be useful should list VIEW only, and hide its own
 * buttons. The route guard and the server check are independent: this one exists
 * so a user does not navigate into a screen whose every request will 403.
 *
 * Usage: `canActivate: [authGuard, permissionGuard(Permissions.CATEGORY_VIEW)]`
 */
export function permissionGuard(...permissions: (PermissionCode | string)[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    const notifications = inject(NotificationService);

    if (auth.hasAny(permissions)) {
      return true;
    }

    notifications.warning('You do not have permission to open that screen.');
    return router.createUrlTree(['/dashboard']);
  };
}
