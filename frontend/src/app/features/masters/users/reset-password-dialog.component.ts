import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { NotificationService } from '../../../core/services/notification.service';
import { FieldErrorComponent } from '../../../shared/components/field-error.component';
import { ModalComponent } from '../../../shared/components/modal.component';
import { User } from '../../../shared/models/user.model';
import { applyServerErrors, clearServerErrors } from '../../../shared/utils/form-errors';
import { UserService } from './user.service';

/**
 * Administrator password reset.
 *
 * Kept separate from the user form, and explicit about its consequence: the
 * reset signs the user out of every device. That is the correct behaviour - a
 * password is usually reset because it may be compromised - but it is the kind
 * of thing an administrator should be told before they press the button, not
 * after.
 */
@Component({
  selector: 'app-reset-password-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, ModalComponent, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-modal [title]="'Reset password for ' + user().username" (closed)="cancelled.emit()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate id="reset-password-form">
        @if (formError()) {
          <div class="alert alert--error" role="alert">{{ formError() }}</div>
        }

        <div class="alert alert--warning">
          This immediately signs {{ user().fullName }} out of every device. Give them the new
          password directly - it is never e-mailed.
        </div>

        <div class="field" style="margin-top: var(--space-4)">
          <label class="field__label field__label--required" for="newPassword">New password</label>
          <input
            id="newPassword"
            class="input"
            type="text"
            formControlName="newPassword"
            autocomplete="new-password"
            [class.input--invalid]="invalid()"
          />
          <p class="field__hint">At least 8 characters, with one letter and one digit.</p>
          <app-field-error [control]="form.controls.newPassword" label="New password" />
        </div>

        <div class="field" style="margin-top: var(--space-3)">
          <label class="checkbox">
            <input type="checkbox" formControlName="mustChangePassword" />
            <span>Require the user to choose their own password at next sign-in</span>
          </label>
        </div>
      </form>

      <ng-container modalActions>
        <button type="button" class="btn" (click)="cancelled.emit()" [disabled]="submitting()">
          Cancel
        </button>
        <button
          type="submit"
          form="reset-password-form"
          class="btn btn--danger"
          [disabled]="submitting()"
        >
          {{ submitting() ? 'Resetting...' : 'Reset password' }}
        </button>
      </ng-container>
    </app-modal>
  `,
})
export class ResetPasswordDialogComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly userService = inject(UserService);
  private readonly notifications = inject(NotificationService);

  readonly user = input.required<User>();

  readonly done = output<void>();
  readonly cancelled = output<void>();

  protected readonly submitting = signal(false);
  protected readonly formError = signal('');

  protected readonly form = this.formBuilder.nonNullable.group({
    newPassword: [
      '',
      [Validators.required, Validators.minLength(8), Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/)],
    ],
    mustChangePassword: [true],
  });

  protected invalid(): boolean {
    const field = this.form.controls.newPassword;
    return field.invalid && (field.dirty || field.touched);
  }

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
    this.userService.resetPassword(this.user().id, this.form.getRawValue()).subscribe({
      next: () => {
        this.submitting.set(false);
        this.notifications.success(`Password reset for ${this.user().username}.`);
        this.done.emit();
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
}
