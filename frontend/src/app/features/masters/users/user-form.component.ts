import { ChangeDetectionStrategy, Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { NotificationService } from '../../../core/services/notification.service';
import { FieldErrorComponent } from '../../../shared/components/field-error.component';
import { ModalComponent } from '../../../shared/components/modal.component';
import { Role, User } from '../../../shared/models/user.model';
import { applyServerErrors, clearServerErrors } from '../../../shared/utils/form-errors';
import { UserService } from './user.service';

/**
 * Create / edit dialog for a user.
 *
 * The password field only exists when creating. Changing an existing user's
 * password is a separate, deliberate action that also ends their open sessions -
 * folding it into a general edit form would make it too easy to do by accident,
 * and would mean every save had to decide whether the password field was
 * meaningfully filled in.
 *
 * The username is immutable once the account exists: it is the identity recorded
 * in every audit column in the database.
 */
@Component({
  selector: 'app-user-form',
  standalone: true,
  imports: [ReactiveFormsModule, ModalComponent, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-modal [title]="user() ? 'Edit user' : 'New user'" (closed)="cancelled.emit()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate id="user-form">
        @if (formError()) {
          <div class="alert alert--error" role="alert">{{ formError() }}</div>
        }

        <div class="form-grid">
          <div class="field">
            <label class="field__label field__label--required" for="username">Username</label>
            <input
              id="username"
              class="input"
              formControlName="username"
              autocomplete="off"
              autocapitalize="none"
              spellcheck="false"
              [class.input--invalid]="invalid('username')"
            />
            @if (user()) {
              <p class="field__hint">The username cannot be changed once the account exists.</p>
            } @else {
              <p class="field__hint">Letters, digits, dot, underscore and hyphen.</p>
            }
            <app-field-error [control]="form.controls.username" label="Username" />
          </div>

          <div class="field">
            <label class="field__label field__label--required" for="fullName">Full name</label>
            <input
              id="fullName"
              class="input"
              formControlName="fullName"
              [class.input--invalid]="invalid('fullName')"
            />
            <app-field-error [control]="form.controls.fullName" label="Full name" />
          </div>

          <div class="field">
            <label class="field__label" for="email">E-mail</label>
            <input
              id="email"
              class="input"
              type="email"
              formControlName="email"
              autocomplete="off"
              [class.input--invalid]="invalid('email')"
            />
            <app-field-error [control]="form.controls.email" label="E-mail" />
          </div>

          <div class="field">
            <label class="field__label" for="mobileNumber">Mobile number</label>
            <input
              id="mobileNumber"
              class="input"
              formControlName="mobileNumber"
              inputmode="tel"
              [class.input--invalid]="invalid('mobileNumber')"
            />
            <app-field-error [control]="form.controls.mobileNumber" label="Mobile number" />
          </div>

          <div class="field field--full">
            <label class="field__label field__label--required" for="roleId">Role</label>
            <select id="roleId" class="select" formControlName="roleId">
              @for (role of roles(); track role.id) {
                <option [ngValue]="role.id">{{ role.label }}</option>
              }
            </select>
            <p class="field__hint">
              Administrators hold every permission. A standard user starts with none - grant them
              individually from the Permissions screen.
            </p>
          </div>

          @if (!user()) {
            <div class="field field--full">
              <label class="field__label field__label--required" for="password">
                Temporary password
              </label>
              <input
                id="password"
                class="input"
                type="text"
                formControlName="password"
                autocomplete="new-password"
                [class.input--invalid]="invalid('password')"
              />
              <p class="field__hint">
                At least 8 characters with one letter and one digit. The user is asked to change it
                at first sign-in.
              </p>
              <app-field-error [control]="form.controls.password" label="Password" />
            </div>
          }
        </div>
      </form>

      <ng-container modalActions>
        <button type="button" class="btn" (click)="cancelled.emit()" [disabled]="submitting()">
          Cancel
        </button>
        <button type="submit" form="user-form" class="btn btn--primary" [disabled]="submitting()">
          {{ submitting() ? 'Saving...' : 'Save' }}
        </button>
      </ng-container>
    </app-modal>
  `,
})
export class UserFormComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly userService = inject(UserService);
  private readonly notifications = inject(NotificationService);

  readonly user = input<User | null>(null);
  readonly roles = input.required<Role[]>();

  readonly saved = output<void>();
  readonly cancelled = output<void>();

  protected readonly submitting = signal(false);
  protected readonly formError = signal('');

  protected readonly form = this.formBuilder.group({
    username: this.formBuilder.nonNullable.control('', [
      Validators.required,
      Validators.minLength(3),
      Validators.maxLength(50),
      Validators.pattern(/^[A-Za-z0-9._-]+$/),
    ]),
    fullName: this.formBuilder.nonNullable.control('', [
      Validators.required,
      Validators.maxLength(150),
    ]),
    email: this.formBuilder.nonNullable.control('', [Validators.email, Validators.maxLength(150)]),
    mobileNumber: this.formBuilder.nonNullable.control('', [
      Validators.pattern(/^$|^[0-9+][0-9 -]{5,19}$/),
    ]),
    roleId: this.formBuilder.control<number | null>(null, Validators.required),
    password: this.formBuilder.nonNullable.control('', [
      Validators.required,
      Validators.minLength(8),
      Validators.pattern(/^(?=.*[A-Za-z])(?=.*\d).+$/),
    ]),
  });

  /** In ngOnInit: bound inputs (user, roles) are not available in the constructor. */
  ngOnInit(): void {
    const user = this.user();
    const roles = this.roles();

    // Default to the least privileged role. A new account should never start out
    // as an administrator because somebody forgot to change a dropdown.
    const defaultRole = roles.find((role) => role.name === 'ROLE_USER') ?? roles[0];
    const currentRole = user ? roles.find((role) => user.roles.includes(role.name)) : undefined;
    this.form.controls.roleId.setValue((currentRole ?? defaultRole)?.id ?? null);

    if (user) {
      this.form.patchValue({
        username: user.username,
        fullName: user.fullName,
        email: user.email ?? '',
        mobileNumber: user.mobileNumber ?? '',
      });
      // Immutable identity, and not sent on update - disabling keeps it out of
      // getRawValue()'s meaning rather than merely out of the user's reach.
      this.form.controls.username.disable();
      this.form.controls.password.disable();
      this.form.controls.password.clearValidators();
    }
  }

  protected invalid(control: 'username' | 'fullName' | 'email' | 'mobileNumber' | 'password'): boolean {
    const field = this.form.controls[control];
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

    const value = this.form.getRawValue();
    const roleIds = value.roleId === null ? undefined : [value.roleId];
    const existing = this.user();
    this.submitting.set(true);

    const request$ = existing
      ? this.userService.update(existing.id, {
          fullName: value.fullName.trim(),
          email: value.email.trim() || null,
          mobileNumber: value.mobileNumber.trim() || null,
          roleIds,
        })
      : this.userService.create({
          username: value.username.trim(),
          fullName: value.fullName.trim(),
          email: value.email.trim() || null,
          mobileNumber: value.mobileNumber.trim() || null,
          password: value.password,
          roleIds,
          mustChangePassword: true,
        });

    request$.subscribe({
      next: () => {
        this.submitting.set(false);
        this.notifications.success(existing ? 'User updated.' : 'User created.');
        this.saved.emit();
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
