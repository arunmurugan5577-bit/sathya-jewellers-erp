import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { Location } from '@angular/common';

import { AuthService } from '../../../core/auth/auth.service';
import { Permissions } from '../../../core/auth/permissions';
import { NotificationService } from '../../../core/services/notification.service';
import { PageHeaderComponent } from '../../../shared/components/page-header.component';
import { SpinnerComponent } from '../../../shared/components/spinner.component';
import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { ModulePermissions, Permission, UserPermissions } from '../../../shared/models/user.model';
import { UserService } from './user.service';

/**
 * The permission matrix for one user.
 *
 * A grid of module rows against the four actions, which is how an administrator
 * thinks about access: "can Ramesh create categories?" rather than "does Ramesh
 * hold CATEGORY_CREATE".
 *
 * Three behaviours worth knowing:
 *
 * - A permission the user already holds **through their role** is shown ticked
 *   and disabled. Unticking it would change nothing, so the UI does not pretend
 *   otherwise. For an administrator that is the entire grid.
 * - The whole set is submitted at once, not as add/remove deltas. Two
 *   administrators editing the same user cannot then silently merge into a state
 *   neither of them chose.
 * - Saving revokes that user's sessions server-side, so the change takes effect
 *   on their next request rather than whenever their token happens to expire.
 */
@Component({
  selector: 'app-user-permissions',
  standalone: true,
  imports: [PageHeaderComponent, SpinnerComponent, HasPermissionDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-header
        [title]="data() ? 'Permissions for ' + data()!.fullName : 'Permissions'"
        [subtitle]="data() ? '@' + data()!.username : ''"
      >
        <button type="button" class="btn" (click)="goBack()">Back</button>
        <button
          *appHasPermission="permissionCodes.USER_EDIT"
          type="button"
          class="btn btn--primary"
          [disabled]="saving() || !dirty() || (data()?.administrator ?? false)"
          (click)="save()"
        >
          {{ saving() ? 'Saving...' : 'Save permissions' }}
        </button>
      </app-page-header>

      @if (loading()) {
        <app-spinner label="Loading permissions..." />
      } @else if (data(); as permissions) {
        @if (permissions.administrator) {
          <div class="alert alert--info">
            This user is an administrator and already holds every permission. Remove the
            administrator role first if their access should be limited.
          </div>
        } @else if (isSelf()) {
          <div class="alert alert--warning">
            You cannot change your own permissions. Ask another administrator to do it.
          </div>
        }

        <section class="card">
          <div class="card__header">
            <h2 class="card__title">Module access</h2>
            @if (dirty()) {
              <span class="badge badge--info">Unsaved changes</span>
            }
          </div>

          <div class="table-wrapper">
            <table class="table matrix">
              <thead>
                <tr>
                  <th>Module</th>
                  <th class="matrix__action">View</th>
                  <th class="matrix__action">Create</th>
                  <th class="matrix__action">Edit</th>
                  <th class="matrix__action">Delete</th>
                  <th class="matrix__action">Export</th>
                </tr>
              </thead>
              <tbody>
                @for (module of permissions.modules; track module.module) {
                  <tr>
                    <td>{{ module.label }}</td>
                    @for (action of actions; track action) {
                      <td class="matrix__action">
                        @if (find(module, action); as permission) {
                          <label
                            class="checkbox"
                            [attr.aria-label]="action + ' ' + module.label"
                          >
                            <input
                              type="checkbox"
                              [checked]="isChecked(permission)"
                              [disabled]="isLocked(permission)"
                              (change)="toggle(permission)"
                            />
                          </label>
                        } @else {
                          <span class="text-muted" aria-hidden="true">&ndash;</span>
                        }
                      </td>
                    }
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </section>

        <p class="text-muted">
          Granting or removing access signs this user out, so the change applies the next time
          they use the application.
        </p>
      }
    </div>
  `,
  styles: [
    `
      .matrix__action {
        text-align: center;
        width: 90px;
      }

      .matrix td.matrix__action .checkbox {
        justify-content: center;
      }

      .matrix tbody td:first-child {
        font-weight: 550;
      }
    `,
  ],
})
export class UserPermissionsComponent {
  private readonly userService = inject(UserService);
  private readonly notifications = inject(NotificationService);
  private readonly location = inject(Location);
  private readonly auth = inject(AuthService);

  /** Bound from the route by `withComponentInputBinding`. */
  readonly id = input.required<string>();

  protected readonly permissionCodes = Permissions;
  protected readonly actions = ['VIEW', 'CREATE', 'EDIT', 'DELETE', 'EXPORT'] as const;

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly data = signal<UserPermissions | null>(null);

  /** The working set of ticked, editable permission ids. */
  protected readonly selected = signal<Set<number>>(new Set());
  private originalSelection = new Set<number>();

  protected readonly dirty = computed(() => {
    const current = this.selected();
    if (current.size !== this.originalSelection.size) {
      return true;
    }
    for (const id of current) {
      if (!this.originalSelection.has(id)) {
        return true;
      }
    }
    return false;
  });

  protected readonly isSelf = computed(() => this.auth.user()?.id === this.data()?.userId);

  constructor() {
    effect(() => {
      const userId = Number(this.id());
      if (Number.isNaN(userId)) {
        return;
      }
      this.load(userId);
    });
  }

  private load(userId: number): void {
    this.loading.set(true);
    this.userService.permissions(userId).subscribe({
      next: (permissions) => {
        this.data.set(permissions);
        this.originalSelection = new Set(permissions.directPermissionIds);
        this.selected.set(new Set(permissions.directPermissionIds));
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected find(module: ModulePermissions, action: string): Permission | undefined {
    return module.permissions.find((permission) => permission.action === action);
  }

  /** Ticked when granted directly, or already implied by a role. */
  protected isChecked(permission: Permission): boolean {
    return this.selected().has(permission.id) || this.isFromRole(permission);
  }

  /** Locked when the role already grants it, or the screen is read-only. */
  protected isLocked(permission: Permission): boolean {
    return (
      this.isFromRole(permission) ||
      this.isSelf() ||
      !this.auth.has(Permissions.USER_EDIT) ||
      (this.data()?.administrator ?? false)
    );
  }

  private isFromRole(permission: Permission): boolean {
    return this.data()?.rolePermissionCodes.includes(permission.code) ?? false;
  }

  protected toggle(permission: Permission): void {
    if (this.isLocked(permission)) {
      return;
    }
    this.selected.update((current) => {
      const next = new Set(current);
      if (next.has(permission.id)) {
        next.delete(permission.id);
      } else {
        next.add(permission.id);
      }
      return next;
    });
  }

  protected save(): void {
    const data = this.data();
    if (!data || this.saving()) {
      return;
    }

    this.saving.set(true);
    this.userService.updatePermissions(data.userId, [...this.selected()]).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.data.set(updated);
        this.originalSelection = new Set(updated.directPermissionIds);
        this.selected.set(new Set(updated.directPermissionIds));
        this.notifications.success(`Permissions updated for ${updated.username}.`);
      },
      error: () => this.saving.set(false),
    });
  }

  protected goBack(): void {
    this.location.back();
  }
}
