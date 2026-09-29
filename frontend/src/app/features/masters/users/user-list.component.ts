import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { Permissions } from '../../../core/auth/permissions';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NotificationService } from '../../../core/services/notification.service';
import { DataTableComponent } from '../../../shared/components/data-table.component';
import { PageHeaderComponent } from '../../../shared/components/page-header.component';
import { PaginatorComponent } from '../../../shared/components/paginator.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { DEFAULT_PAGE_SIZE, Page, emptyPage } from '../../../shared/models/page.model';
import { Role, User } from '../../../shared/models/user.model';
import { ResetPasswordDialogComponent } from './reset-password-dialog.component';
import { UserFormComponent } from './user-form.component';
import { UserService } from './user.service';

/**
 * User administration.
 *
 * Does not reuse the master list base class: users have their own operations
 * (reset password, permissions) and their own safety rules, and bending the
 * shared base to fit would make it worse for the five masters that do fit.
 *
 * The signed-in user's own row has its destructive actions removed. The server
 * refuses them anyway - you cannot deactivate or demote yourself - but offering
 * a button that always fails is not useful.
 */
@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
    PageHeaderComponent,
    DataTableComponent,
    PaginatorComponent,
    StatusBadgeComponent,
    HasPermissionDirective,
    UserFormComponent,
    ResetPasswordDialogComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-header
        title="Users"
        subtitle="Accounts, their status and the permissions granted to each."
      >
        <button
          *appHasPermission="permissions.USER_CREATE"
          type="button"
          class="btn btn--primary"
          (click)="openCreate()"
        >
          New user
        </button>
      </app-page-header>

      <section class="card">
        <div class="toolbar">
          <div class="toolbar__field">
            <label class="toolbar__label" for="search">Search</label>
            <input
              id="search"
              class="input"
              type="search"
              placeholder="Username, name or e-mail"
              [value]="search()"
              (input)="onSearchInput($event)"
            />
          </div>

          <div class="toolbar__field">
            <label class="toolbar__label" for="status">Status</label>
            <select id="status" class="select" [value]="statusFilter()" (change)="onStatusChange($event)">
              <option value="">All</option>
              <option value="true">Active</option>
              <option value="false">Inactive</option>
            </select>
          </div>
        </div>

        <app-data-table
          [loading]="loading()"
          [isEmpty]="page().content.length === 0"
          emptyTitle="No users match these filters"
          emptyMessage="Every user needs an account before they can sign in."
        >
          <table class="table">
            <thead>
              <tr>
                <th>Username</th>
                <th>Full name</th>
                <th>Role</th>
                <th>Contact</th>
                <th>Last sign-in</th>
                <th>Status</th>
                <th class="td--actions">Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (user of page().content; track user.id) {
                <tr>
                  <td>
                    <code>{{ user.username }}</code>
                    @if (isSelf(user)) {
                      <span class="badge badge--info">You</span>
                    }
                  </td>
                  <td>{{ user.fullName }}</td>
                  <td>
                    @for (role of user.roles; track role) {
                      <span class="badge badge--muted">{{ roleLabel(role) }}</span>
                    }
                  </td>
                  <td class="truncate">{{ user.email || user.mobileNumber || '-' }}</td>
                  <td>
                    {{ user.lastLoginAt ? (user.lastLoginAt | date: 'dd MMM yyyy, HH:mm') : 'Never' }}
                  </td>
                  <td>
                    <app-status-badge [active]="user.active" />
                    @if (user.mustChangePassword) {
                      <span class="badge badge--info">Must change password</span>
                    }
                  </td>
                  <td class="td--actions">
                    <div class="row row--end">
                      <a
                        *appHasPermission="permissions.USER_VIEW"
                        class="btn btn--sm"
                        [routerLink]="['/masters/users', user.id, 'permissions']"
                      >
                        Permissions
                      </a>
                      <button
                        *appHasPermission="permissions.USER_EDIT"
                        type="button"
                        class="btn btn--sm"
                        (click)="openEdit(user)"
                      >
                        Edit
                      </button>
                      @if (!isSelf(user)) {
                        <button
                          *appHasPermission="permissions.USER_EDIT"
                          type="button"
                          class="btn btn--sm"
                          (click)="resetting.set(user)"
                        >
                          Reset password
                        </button>
                        <button
                          *appHasPermission="permissions.USER_EDIT"
                          type="button"
                          class="btn btn--sm"
                          (click)="toggleActive(user)"
                        >
                          {{ user.active ? 'Deactivate' : 'Activate' }}
                        </button>
                      }
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </app-data-table>

        <app-paginator
          [page]="page()"
          (pageChange)="reload($event)"
          (sizeChange)="onSizeChange($event)"
        />
      </section>
    </div>

    @if (dialogOpen()) {
      <app-user-form
        [user]="editing()"
        [roles]="roles()"
        (saved)="onSaved()"
        (cancelled)="closeDialog()"
      />
    }

    @if (resetting(); as target) {
      <app-reset-password-dialog
        [user]="target"
        (done)="resetting.set(null); reload()"
        (cancelled)="resetting.set(null)"
      />
    }
  `,
  styles: [
    `
      code {
        font-family: var(--font-mono);
        font-size: var(--text-sm);
      }

      .badge + .badge {
        margin-left: var(--space-1);
      }

      td .badge {
        margin-left: var(--space-2);
      }
    `,
  ],
})
export class UserListComponent {
  private readonly userService = inject(UserService);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly searchInput$ = new Subject<string>();

  protected readonly permissions = Permissions;
  protected readonly loading = signal(true);
  protected readonly page = signal<Page<User>>(emptyPage<User>());
  protected readonly search = signal('');
  protected readonly statusFilter = signal<'' | 'true' | 'false'>('');
  protected readonly roles = signal<Role[]>([]);

  protected readonly editing = signal<User | null>(null);
  protected readonly dialogOpen = signal(false);
  protected readonly resetting = signal<User | null>(null);

  constructor() {
    this.searchInput$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((term) => {
        this.search.set(term);
        this.reload(0);
      });

    this.userService.roles().subscribe({ next: (roles) => this.roles.set(roles) });
    this.reload(0);
  }

  protected reload(page = this.page().page): void {
    this.loading.set(true);
    this.userService
      .list(
        { page, size: this.page().size || DEFAULT_PAGE_SIZE, sort: 'username', direction: 'asc' },
        {
          search: this.search() || undefined,
          active: this.statusFilter() === '' ? undefined : this.statusFilter(),
        },
      )
      .subscribe({
        next: (result) => {
          this.page.set(result);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  protected onSearchInput(event: Event): void {
    this.searchInput$.next((event.target as HTMLInputElement).value);
  }

  protected onStatusChange(event: Event): void {
    this.statusFilter.set((event.target as HTMLSelectElement).value as '' | 'true' | 'false');
    this.reload(0);
  }

  protected onSizeChange(size: number): void {
    this.page.update((current) => ({ ...current, size }));
    this.reload(0);
  }

  protected isSelf(user: User): boolean {
    return this.auth.user()?.id === user.id;
  }

  protected roleLabel(roleName: string): string {
    return this.roles().find((role) => role.name === roleName)?.label ?? roleName;
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.dialogOpen.set(true);
  }

  protected openEdit(user: User): void {
    this.editing.set(user);
    this.dialogOpen.set(true);
  }

  protected closeDialog(): void {
    this.dialogOpen.set(false);
    this.editing.set(null);
  }

  protected onSaved(): void {
    this.closeDialog();
    this.reload();
  }

  protected async toggleActive(user: User): Promise<void> {
    if (user.active) {
      const confirmed = await this.confirm.ask({
        title: `Deactivate ${user.username}?`,
        message:
          'They will be signed out immediately and will not be able to sign in again until the ' +
          'account is reactivated.',
        confirmLabel: 'Deactivate',
        danger: true,
      });
      if (!confirmed) {
        return;
      }
    }

    this.userService.setActive(user.id, !user.active).subscribe({
      next: () => {
        this.notifications.success(`${user.username} ${user.active ? 'deactivated' : 'activated'}.`);
        this.reload();
      },
    });
  }
}
