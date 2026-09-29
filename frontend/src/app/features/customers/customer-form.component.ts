import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { SUPPRESS_ERROR_TOAST } from '../../core/interceptors/error.interceptor';
import { NotificationService } from '../../core/services/notification.service';
import { FieldErrorComponent } from '../../shared/components/field-error.component';
import { ModalComponent } from '../../shared/components/modal.component';
import { Customer, CustomerRequest } from '../../shared/models/sales.model';
import { applyServerErrors, clearServerErrors, markAllTouched } from '../../shared/utils/form-errors';
import { CustomerApiService } from '../sales/sales-api.service';

/** Create or edit a customer. Used from the customer list and, inline, from the sale counter. */
@Component({
  selector: 'app-customer-form',
  standalone: true,
  imports: [ReactiveFormsModule, ModalComponent, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-modal [title]="customer() ? 'Edit customer ' + customer()!.customerCode : 'New customer'" (closed)="closed.emit()">
      <form class="form-grid" [formGroup]="form" (ngSubmit)="save()" id="customerForm">
        <div class="field field--full">
          <label class="field__label field__label--required" for="fullName">Name</label>
          <input id="fullName" class="input" formControlName="fullName" autocomplete="off" />
          <app-field-error [control]="form.controls.fullName" label="Name" />
        </div>
        <div class="field">
          <label class="field__label" for="mobileNumber">Mobile</label>
          <input id="mobileNumber" class="input" formControlName="mobileNumber" inputmode="tel" />
          <app-field-error [control]="form.controls.mobileNumber" label="Mobile" />
        </div>
        <div class="field">
          <label class="field__label" for="email">E-mail</label>
          <input id="email" class="input" formControlName="email" type="email" />
          <app-field-error [control]="form.controls.email" label="E-mail" />
        </div>
        <div class="field field--full">
          <label class="field__label" for="addressLine1">Address</label>
          <input id="addressLine1" class="input" formControlName="addressLine1" />
          <app-field-error [control]="form.controls.addressLine1" label="Address" />
        </div>
        <div class="field field--full">
          <input class="input" formControlName="addressLine2" aria-label="Address line 2" />
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
          <input id="pincode" class="input" formControlName="pincode" inputmode="numeric" maxlength="6" />
          <app-field-error [control]="form.controls.pincode" label="Pincode" />
        </div>
        <div class="field">
          <label class="field__label" for="gstin">GSTIN</label>
          <input id="gstin" class="input" formControlName="gstin" maxlength="15" />
          <app-field-error [control]="form.controls.gstin" label="GSTIN" />
        </div>
        <div class="field">
          <label class="field__label" for="pan">PAN</label>
          <input id="pan" class="input" formControlName="pan" maxlength="10" />
          <app-field-error [control]="form.controls.pan" label="PAN" />
        </div>
        @for (message of formErrors(); track message) {
          <div class="alert alert--error field--full">{{ message }}</div>
        }
      </form>

      <ng-container modalActions>
        <button type="button" class="btn btn--ghost" (click)="closed.emit()">Cancel</button>
        <button type="submit" form="customerForm" class="btn btn--primary" [disabled]="saving()">
          {{ saving() ? 'Saving...' : 'Save customer' }}
        </button>
      </ng-container>
    </app-modal>
  `,
})
export class CustomerFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(CustomerApiService);
  private readonly notifications = inject(NotificationService);

  readonly customer = input<Customer | null>(null);
  /** Pre-fills the name or mobile typed into a search box before "New customer" was pressed. */
  readonly initialText = input<string>('');
  readonly saved = output<Customer>();
  readonly closed = output<void>();

  protected readonly saving = signal(false);
  protected readonly formErrors = signal<string[]>([]);

  protected readonly form = this.fb.nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(150)]],
    mobileNumber: ['', Validators.pattern(/^$|^[0-9+][0-9 -]{5,19}$/)],
    email: ['', [Validators.email, Validators.maxLength(150)]],
    addressLine1: ['', Validators.maxLength(200)],
    addressLine2: ['', Validators.maxLength(200)],
    city: ['', Validators.maxLength(100)],
    state: ['', Validators.maxLength(100)],
    pincode: ['', Validators.pattern(/^$|^[0-9]{6}$/)],
    gstin: [''],
    pan: ['', Validators.pattern(/^$|^[A-Za-z]{5}[0-9]{4}[A-Za-z]$/)],
  });

  ngOnInit(): void {
    const existing = this.customer();
    if (existing) {
      this.form.patchValue({
        fullName: existing.fullName,
        mobileNumber: existing.mobileNumber ?? '',
        email: existing.email ?? '',
        addressLine1: existing.addressLine1 ?? '',
        addressLine2: existing.addressLine2 ?? '',
        city: existing.city ?? '',
        state: existing.state ?? '',
        pincode: existing.pincode ?? '',
        gstin: existing.gstin ?? '',
        pan: existing.pan ?? '',
      });
    } else {
      const text = this.initialText().trim();
      if (/^[0-9+][0-9 -]{5,}$/.test(text)) {
        this.form.controls.mobileNumber.setValue(text);
      } else if (text) {
        this.form.controls.fullName.setValue(text);
      }
    }
  }

  protected save(): void {
    clearServerErrors(this.form);
    this.formErrors.set([]);
    if (this.form.invalid) {
      markAllTouched(this.form);
      return;
    }
    const raw = this.form.getRawValue();
    const request: CustomerRequest = {
      ...raw,
      gstin: raw.gstin.trim().toUpperCase() || null,
      pan: raw.pan.trim().toUpperCase() || null,
    };
    const context = new HttpContext().set(SUPPRESS_ERROR_TOAST, true);
    const existing = this.customer();
    const call = existing ? this.api.update(existing.id, request, context) : this.api.create(request, context);

    this.saving.set(true);
    call.subscribe({
      next: (customer) => {
        this.saving.set(false);
        this.notifications.success(`Customer ${customer.customerCode} saved.`);
        this.saved.emit(customer);
      },
      error: (error) => {
        this.saving.set(false);
        this.formErrors.set(applyServerErrors(this.form, error));
      },
    });
  }
}
