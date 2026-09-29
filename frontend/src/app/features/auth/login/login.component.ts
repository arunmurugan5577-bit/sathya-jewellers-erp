import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { FieldErrorComponent } from '../../../shared/components/field-error.component';
import { isApiError } from '../../../shared/models/api-error.model';

/**
 * Sign-in screen.
 *
 * Renders outside the shell, so a signed-out user never sees navigation for
 * screens they cannot reach. The error is shown inline rather than as a toast:
 * a failed login is about this form, and the message belongs where the user is
 * looking.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="login">
      <div class="login__panel">
        <div class="login__brand">
          <img class="login__logo" src="assets/brand/logo-wide.png" alt="Sathya Jewellers" width="774" height="258" />
          <p class="login__subtitle">Sign in to continue</p>
        </div>

        @if (errorMessage()) {
          <div class="alert alert--error" role="alert">{{ errorMessage() }}</div>
        }

        <form class="login__form" [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <div class="field">
            <label class="field__label field__label--required" for="username">Username</label>
            <input
              id="username"
              class="input"
              formControlName="username"
              autocomplete="username"
              autocapitalize="none"
              spellcheck="false"
              [class.input--invalid]="isInvalid('username')"
              [attr.aria-invalid]="isInvalid('username')"
            />
            <app-field-error [control]="form.controls.username" label="Username" />
          </div>

          <div class="field">
            <label class="field__label field__label--required" for="password">Password</label>
            <div class="login__password">
              <input
                id="password"
                class="input"
                formControlName="password"
                [type]="showPassword() ? 'text' : 'password'"
                autocomplete="current-password"
                [class.input--invalid]="isInvalid('password')"
                [attr.aria-invalid]="isInvalid('password')"
              />
              <button
                type="button"
                class="btn btn--ghost btn--sm login__reveal"
                (click)="showPassword.set(!showPassword())"
              >
                {{ showPassword() ? 'Hide' : 'Show' }}
              </button>
            </div>
            <app-field-error [control]="form.controls.password" label="Password" />
          </div>

          <button type="submit" class="btn btn--primary btn--block" [disabled]="submitting()">
            {{ submitting() ? 'Signing in...' : 'Sign in' }}
          </button>
        </form>
      </div>
    </div>
  `,
  styles: [
    `
      .login {
        display: grid;
        place-items: center;
        min-height: 100vh;
        padding: var(--space-4);
        /* The maroon field with a faint gold bloom - the sign-in screen is the
         * first impression, so it carries the identity most strongly. */
        background:
          radial-gradient(circle at 18% 12%, rgba(201, 162, 39, 0.18), transparent 42%),
          radial-gradient(circle at 85% 88%, rgba(124, 17, 40, 0.35), transparent 55%),
          var(--brand-deepest);
      }

      .login__panel {
        width: min(400px, 100%);
        padding: var(--space-6);
        background: var(--surface-card);
        border: 1px solid var(--border-subtle);
        border-radius: var(--radius-lg);
        box-shadow: var(--shadow-lg);
      }

      .login__brand {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--space-3);
        margin-bottom: var(--space-5);
        text-align: center;
      }

      .login__logo {
        display: block;
        width: min(260px, 100%);
        height: auto;
        border-radius: var(--radius-md);
      }

      .login__subtitle {
        color: var(--text-muted);
        font-size: var(--text-base);
      }

      .login__form {
        display: flex;
        flex-direction: column;
        gap: var(--space-4);
        margin-top: var(--space-5);
      }

      .login__password {
        position: relative;
      }

      .login__reveal {
        position: absolute;
        top: 50%;
        right: var(--space-1);
        transform: translateY(-50%);
      }
    `,
  ],
})
export class LoginComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly submitting = signal(false);
  protected readonly showPassword = signal(false);
  protected readonly errorMessage = signal('');

  protected readonly form = this.formBuilder.nonNullable.group({
    username: ['', [Validators.required, Validators.maxLength(50)]],
    password: ['', [Validators.required, Validators.maxLength(100)]],
  });

  protected isInvalid(control: 'username' | 'password'): boolean {
    const field = this.form.controls[control];
    return field.invalid && (field.dirty || field.touched);
  }

  protected submit(): void {
    if (this.submitting()) {
      return; // Guards against a double submit from a fast second click.
    }
    this.errorMessage.set('');

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.auth
      .login(this.form.getRawValue())
      .subscribe({
        next: () => {
          const redirect = this.route.snapshot.queryParamMap.get('redirect');
          void this.router.navigateByUrl(
            this.auth.mustChangePassword() ? '/change-password' : (redirect ?? '/dashboard'),
          );
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.errorMessage.set(describeLoginFailure(error));
          this.form.controls.password.reset();
        },
      });
  }
}

function describeLoginFailure(error: unknown): string {
  const body = (error as { error?: unknown })?.error;
  if (isApiError(body)) {
    return body.message;
  }
  if ((error as { status?: number })?.status === 0) {
    return 'Cannot reach the server. Check that the API is running.';
  }
  return 'Sign in failed. Please try again.';
}
