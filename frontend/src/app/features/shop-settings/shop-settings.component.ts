import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../../core/auth/auth.service';
import { Permissions } from '../../core/auth/permissions';
import { ApiService } from '../../core/services/api.service';
import { NotificationService } from '../../core/services/notification.service';
import { FieldErrorComponent } from '../../shared/components/field-error.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { SpinnerComponent } from '../../shared/components/spinner.component';
import { ShopSettings } from '../../shared/models/shop.model';
import { applyServerErrors, clearServerErrors } from '../../shared/utils/form-errors';

/**
 * Shop profile.
 *
 * These values end up on invoices, receipts and GST returns, so the formats are
 * validated here and again on the server and in the database. Only the shop name
 * is mandatory - a business that is not yet GST registered still has to be able
 * to use the system.
 *
 * The form is read-only for a user who can view but not edit, rather than hidden
 * entirely: knowing the shop's own GSTIN is routinely useful to staff who cannot
 * change it.
 */
@Component({
  selector: 'app-shop-settings',
  standalone: true,
  imports: [ReactiveFormsModule, PageHeaderComponent, SpinnerComponent, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-header
        title="Shop Details"
        subtitle="Used on invoices, receipts and GST reports."
      />

      @if (loading()) {
        <app-spinner label="Loading shop details..." />
      } @else {
        @if (!canEdit) {
          <div class="alert alert--info">
            You can view these details but not change them.
          </div>
        }

        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          @if (formError()) {
            <div class="alert alert--error" role="alert">{{ formError() }}</div>
          }

          <section class="card">
            <div class="card__header"><h2 class="card__title">Identity</h2></div>
            <div class="card__body">
              <div class="form-grid">
                <div class="field field--full">
                  <label class="field__label field__label--required" for="shopName">Shop name</label>
                  <input
                    id="shopName"
                    class="input"
                    formControlName="shopName"
                    [class.input--invalid]="invalid('shopName')"
                  />
                  <app-field-error [control]="form.controls.shopName" label="Shop name" />
                </div>

                <div class="field">
                  <label class="field__label" for="gstin">GSTIN</label>
                  <input
                    id="gstin"
                    class="input gstin"
                    formControlName="gstin"
                    maxlength="15"
                    autocapitalize="characters"
                    placeholder="33AAAAA0000A1Z5"
                    [class.input--invalid]="invalid('gstin')"
                  />
                  <p class="field__hint">15 characters. Leave blank if not registered.</p>
                  <app-field-error [control]="form.controls.gstin" label="GSTIN" />
                </div>
              </div>
            </div>
          </section>

          <section class="card">
            <div class="card__header"><h2 class="card__title">Address</h2></div>
            <div class="card__body">
              <div class="form-grid">
                <div class="field field--full">
                  <label class="field__label" for="addressLine1">Address line 1</label>
                  <input id="addressLine1" class="input" formControlName="addressLine1" />
                </div>

                <div class="field field--full">
                  <label class="field__label" for="addressLine2">Address line 2</label>
                  <input id="addressLine2" class="input" formControlName="addressLine2" />
                </div>

                <div class="field">
                  <label class="field__label" for="city">City</label>
                  <input id="city" class="input" formControlName="city" />
                </div>

                <div class="field">
                  <label class="field__label" for="state">State</label>
                  <input id="state" class="input" formControlName="state" />
                </div>

                <div class="field">
                  <label class="field__label" for="pincode">Pincode</label>
                  <input
                    id="pincode"
                    class="input"
                    formControlName="pincode"
                    inputmode="numeric"
                    maxlength="6"
                    [class.input--invalid]="invalid('pincode')"
                  />
                  <app-field-error [control]="form.controls.pincode" label="Pincode" />
                </div>
              </div>
            </div>
          </section>

          <section class="card">
            <div class="card__header"><h2 class="card__title">Contact</h2></div>
            <div class="card__body">
              <div class="form-grid">
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

                <div class="field">
                  <label class="field__label" for="alternateMobileNumber">Alternate number</label>
                  <input
                    id="alternateMobileNumber"
                    class="input"
                    formControlName="alternateMobileNumber"
                    inputmode="tel"
                    [class.input--invalid]="invalid('alternateMobileNumber')"
                  />
                  <app-field-error
                    [control]="form.controls.alternateMobileNumber"
                    label="Alternate number"
                  />
                </div>

                <div class="field">
                  <label class="field__label" for="email">E-mail</label>
                  <input
                    id="email"
                    class="input"
                    type="email"
                    formControlName="email"
                    [class.input--invalid]="invalid('email')"
                  />
                  <app-field-error [control]="form.controls.email" label="E-mail" />
                </div>
              </div>
            </div>
          </section>

          @if (canEdit) {
            <div class="row row--end">
              <button type="button" class="btn" (click)="reset()" [disabled]="submitting()">
                Discard changes
              </button>
              <button type="submit" class="btn btn--primary" [disabled]="submitting()">
                {{ submitting() ? 'Saving...' : 'Save changes' }}
              </button>
            </div>
          }
        </form>
      }
    </div>
  `,
  styles: [
    `
      form {
        display: flex;
        flex-direction: column;
        gap: var(--space-5);
      }

      .gstin {
        font-family: var(--font-mono);
        letter-spacing: 0.06em;
      }
    `,
  ],
})
export class ShopSettingsComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly api = inject(ApiService);
  private readonly notifications = inject(NotificationService);
  private readonly auth = inject(AuthService);

  protected readonly canEdit = this.auth.has(Permissions.SHOP_SETTINGS_EDIT);
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly formError = signal('');

  private current: ShopSettings | null = null;

  protected readonly form = this.formBuilder.nonNullable.group({
    shopName: ['', [Validators.required, Validators.maxLength(150)]],
    addressLine1: ['', Validators.maxLength(200)],
    addressLine2: ['', Validators.maxLength(200)],
    city: ['', Validators.maxLength(100)],
    state: ['', Validators.maxLength(100)],
    pincode: ['', Validators.pattern(/^$|^[0-9]{6}$/)],
    mobileNumber: ['', Validators.pattern(/^$|^[0-9+][0-9 -]{5,19}$/)],
    alternateMobileNumber: ['', Validators.pattern(/^$|^[0-9+][0-9 -]{5,19}$/)],
    email: ['', [Validators.email, Validators.maxLength(150)]],
    gstin: ['', Validators.pattern(/^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$/)],
  });

  constructor() {
    this.api.get<ShopSettings>('/shop-settings').subscribe({
      next: (settings) => {
        this.current = settings;
        this.patch(settings);
        this.loading.set(false);
        if (!this.canEdit) {
          this.form.disable();
        }
      },
      error: () => this.loading.set(false),
    });
  }

  private patch(settings: ShopSettings): void {
    this.form.patchValue({
      shopName: settings.shopName,
      addressLine1: settings.addressLine1 ?? '',
      addressLine2: settings.addressLine2 ?? '',
      city: settings.city ?? '',
      state: settings.state ?? '',
      pincode: settings.pincode ?? '',
      mobileNumber: settings.mobileNumber ?? '',
      alternateMobileNumber: settings.alternateMobileNumber ?? '',
      email: settings.email ?? '',
      gstin: settings.gstin ?? '',
    });
    this.form.markAsPristine();
  }

  protected invalid(control: keyof typeof this.form.controls): boolean {
    const field = this.form.controls[control];
    return field.invalid && (field.dirty || field.touched);
  }

  protected reset(): void {
    if (this.current) {
      this.patch(this.current);
    }
    this.formError.set('');
  }

  protected submit(): void {
    if (this.submitting() || !this.canEdit) {
      return;
    }
    this.formError.set('');
    clearServerErrors(this.form);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const value = this.form.getRawValue();
    this.submitting.set(true);

    this.api
      .put<ShopSettings>('/shop-settings', {
        ...value,
        gstin: value.gstin.trim().toUpperCase(),
      })
      .subscribe({
        next: (settings) => {
          this.submitting.set(false);
          this.current = settings;
          this.patch(settings);
          this.notifications.success('Shop details saved.');
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
