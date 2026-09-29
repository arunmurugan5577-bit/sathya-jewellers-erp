import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { SUPPRESS_ERROR_TOAST } from '../../../core/interceptors/error.interceptor';
import { ApiService } from '../../../core/services/api.service';
import { NotificationService } from '../../../core/services/notification.service';
import { FieldErrorComponent } from '../../../shared/components/field-error.component';
import { applyServerErrors, clearServerErrors } from '../../../shared/utils/form-errors';

/**
 * Self-service password change.
 *
 * Also the screen a user is forced to when `mustChangePassword` is set - after
 * the bootstrap administrator's first sign-in, or after an administrator reset
 * their password. In that case there is no way out of it except changing the
 * password, which is the point: a credential somebody else chose should not stay
 * in use.
 */
@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [ReactiveFormsModule, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <div class="panel">
        <h1 class="panel__title">Change password</h1>

        @if (auth.mustChangePassword()) {
          <div class="alert alert--warning" role="alert">
            You are signing in with a password that was set for you. Choose your own before
            continuing.
          </div>
        }

        <form class="panel__form" [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <div class="field">
            <label class="field__label field__label--required" for="currentPassword">
              Current password
            </label>
            <input
              id="currentPassword"
              type="password"
              class="input"
              formControlName="currentPassword"
              autocomplete="current-password"
            />
            <app-field-error [control]="form.controls.currentPassword" label="Current password" />
          </div>

          <div class="field">
            <label class="field__label field__label--required" for="newPassword">New password</label>
            <input
              id="newPassword"
              type="password"
              class="input"
              formControlName="newPassword"
              autocomplete="new-password"
            />
            <p class="field__hint">
              At least 8 characters, with one letter and one digit.
            </p>
            <app-field-error [control]="form.controls.newPassword" label="New password" />
          </div>

          <div class="field">
            <label class="field__label field__label--required" for="confirmPassword">
              Confirm new password
            </label>
            <input
              id="confirmPassword"
              type="password"
              class="input"
              formControlName="confirmPassword"
              autocomplete="new-password"
            />
            @if (form.errors?.['passwordMismatch'] && form.controls.confirmPassword.touched) {
              <p class="field__error">The two passwords do not match.</p>
            }
          </div>

          @if (formError()) {
            <div class="alert alert--error" role="alert">{{ formError() }}</div>
          }

          <div class="panel__actions">
            @if (!auth.mustChangePassword()) {
              <button type="button" class="btn" (click)="cancel()">Cancel</button>
            }
            <button type="submit" class="btn btn--primary" [disabled]="submitting()">
              {{ submitting() ? 'Saving...' : 'Change password' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  `,
  styles: [
    `
      .page {
        display: grid;
        place-items: center;
        min-height: 100vh;
        padding: var(--space-4);
        background: var(--surface-page);
      }

      .panel {
        width: min(440px, 100%);
        padding: var(--space-6);
        background: var(--surface-card);
        border: 1px solid var(--border-subtle);
        border-radius: var(--radius-lg);
        box-shadow: var(--shadow-md);
      }

      .panel__title {
        font-size: var(--text-xl);
        margin-bottom: var(--space-4);
      }

      .panel__form {
        display: flex;
        flex-direction: column;
        gap: var(--space-4);
        margin-top: var(--space-4);
      }

      .panel__actions {
        display: flex;
        justify-content: flex-end;
        gap: var(--space-3);
      }
    `,
  ],
})
export class ChangePasswordComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  protected readonly auth = inject(AuthService);
  protected readonly submitting = signal(false);
  protected readonly formError = signal('');

  protected readonly form = this.formBuilder.nonNullable.group(
    {
      currentPassword: ['', Validators.required],
      newPassword: [
        '',
        [Validators.required, Validators.minLength(8), Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/)],
      ],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );

  protected submit(): void {
    if (this.submitting()) {
      return;
    }
    this.formError.set('');
    clearServerErrors(this.form);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    const { currentPassword, newPassword } = this.form.getRawValue();

    this.api
      .post<void>(
        '/users/me/change-password',
        { currentPassword, newPassword },
        new HttpContext().set(SUPPRESS_ERROR_TOAST, true),
      )
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.auth.markPasswordChanged();
          this.notifications.success('Your password has been changed.');
          // Every session was revoked server-side, including this one's refresh
          // token, so the user signs in again with the new password.
          this.auth.logout();
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          const unmatched = applyServerErrors(this.form, error);
          if (unmatched.length > 0) {
            this.formError.set(unmatched.join(' '));
          }
        },
      });
  }

  protected cancel(): void {
    void this.router.navigate(['/dashboard']);
  }
}

/** Cross-field rule: the confirmation must equal the new password. */
function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const newPassword = group.get('newPassword')?.value as string;
  const confirmPassword = group.get('confirmPassword')?.value as string;
  return newPassword && confirmPassword && newPassword !== confirmPassword
    ? { passwordMismatch: true }
    : null;
}
