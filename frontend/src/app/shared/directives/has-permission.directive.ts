import { Directive, TemplateRef, ViewContainerRef, effect, inject, input } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';
import { PermissionCode } from '../../core/auth/permissions';

/**
 * Structural directive that renders its content only when the signed-in user
 * holds the permission.
 *
 *     <button *appHasPermission="Permissions.CATEGORY_CREATE">New category</button>
 *     <button *appHasPermission="[Permissions.A, Permissions.B]">...</button>
 *
 * This hides controls the user cannot use. It is presentation, not security -
 * every one of those actions is refused by the server independently, and this
 * directive would be worthless as a defence because the user controls the
 * browser. What it is good for is not offering an action that will fail.
 *
 * Reacts to the permission signal, so a permission change picked up at token
 * refresh updates the UI without a reload.
 */
@Directive({
  selector: '[appHasPermission]',
  standalone: true,
})
export class HasPermissionDirective {
  private readonly auth = inject(AuthService);
  private readonly templateRef = inject(TemplateRef<unknown>);
  private readonly viewContainer = inject(ViewContainerRef);

  /** A single permission code, or a list of which any one is sufficient. */
  readonly appHasPermission = input.required<PermissionCode | string | readonly string[]>();

  private rendered = false;

  constructor() {
    effect(() => {
      const required = this.appHasPermission();
      const allowed = Array.isArray(required)
        ? this.auth.hasAny(required)
        : this.auth.has(required as string);

      if (allowed && !this.rendered) {
        this.viewContainer.createEmbeddedView(this.templateRef);
        this.rendered = true;
      } else if (!allowed && this.rendered) {
        this.viewContainer.clear();
        this.rendered = false;
      }
    });
  }
}
