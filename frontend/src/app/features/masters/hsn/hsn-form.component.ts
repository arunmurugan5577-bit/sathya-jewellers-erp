import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

import { FieldErrorComponent } from '../../../shared/components/field-error.component';
import { ModalComponent } from '../../../shared/components/modal.component';
import { HsnCode, HsnCodeRequest } from '../../../shared/models/master.model';
import { applyServerErrors, clearServerErrors } from '../../../shared/utils/form-errors';

/**
 * Create / edit dialog for an HSN code.
 *
 * The GST rate is entered as a decimal and sent as one. It is stored in a
 * NUMERIC column and will eventually multiply invoice values, so it never passes
 * through a binary float on the way.
 */
@Component({
  selector: 'app-hsn-form',
  standalone: true,
  imports: [ReactiveFormsModule, ModalComponent, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-modal [title]="entity() ? 'Edit HSN code' : 'New HSN code'" (closed)="cancelled.emit()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate id="hsn-form">
        @if (formError()) {
          <div class="alert alert--error" role="alert">{{ formError() }}</div>
        }

        <div class="form-grid">
          <div class="field">
            <label class="field__label field__label--required" for="hsnCode">HSN code</label>
            <input
              id="hsnCode"
              class="input"
              formControlName="hsnCode"
              inputmode="numeric"
              [class.input--invalid]="invalid('hsnCode')"
              autocomplete="off"
            />
            <p class="field__hint">4 to 8 digits. Jewellery is usually 7113.</p>
            <app-field-error [control]="form.controls.hsnCode" label="HSN code" />
          </div>

          <div class="field">
            <label class="field__label field__label--required" for="gstPercentage">GST %</label>
            <input
              id="gstPercentage"
              class="input"
              type="number"
              step="0.01"
              min="0"
              max="100"
              formControlName="gstPercentage"
              [class.input--invalid]="invalid('gstPercentage')"
            />
            <p class="field__hint">Up to two decimal places.</p>
            <app-field-error [control]="form.controls.gstPercentage" label="GST percentage" />
          </div>

          <div class="field field--full">
            <label class="field__label" for="description">Description</label>
            <textarea id="description" class="textarea" formControlName="description"></textarea>
          </div>

          <div class="field field--full">
            <label class="checkbox">
              <input type="checkbox" formControlName="active" />
              <span>Active - available for selection on the inventory form</span>
            </label>
          </div>
        </div>
      </form>

      <ng-container modalActions>
        <button type="button" class="btn" (click)="cancelled.emit()" [disabled]="submitting()">
          Cancel
        </button>
        <button type="submit" form="hsn-form" class="btn btn--primary" [disabled]="submitting()">
          {{ submitting() ? 'Saving...' : 'Save' }}
        </button>
      </ng-container>
    </app-modal>
  `,
})
export class HsnFormComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);

  readonly entity = input<HsnCode | null>(null);
  readonly save = input.required<(request: HsnCodeRequest) => Observable<unknown>>();

  readonly saved = output<void>();
  readonly cancelled = output<void>();

  protected readonly submitting = signal(false);
  protected readonly formError = signal('');

  protected readonly form = this.formBuilder.group({
    hsnCode: this.formBuilder.nonNullable.control('', [
      Validators.required,
      Validators.pattern(/^[0-9]{4,8}$/),
    ]),
    gstPercentage: this.formBuilder.control<number | null>(null, [
      Validators.required,
      Validators.min(0),
      Validators.max(100),
    ]),
    description: this.formBuilder.nonNullable.control('', Validators.maxLength(500)),
    active: this.formBuilder.nonNullable.control(true),
  });

  /**
   * Prefills the form in edit mode.
   *
   * In ngOnInit rather than the constructor: bound inputs are not set until
   * after construction, so reading them there would silently see the initial
   * value and leave every edit dialog blank.
   */
  ngOnInit(): void {
    const entity = this.entity();
    if (entity) {
      this.form.patchValue({
        hsnCode: entity.hsnCode,
        gstPercentage: entity.gstPercentage,
        description: entity.description ?? '',
        active: entity.active,
      });
    }
  }

  protected invalid(control: 'hsnCode' | 'gstPercentage'): boolean {
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
    this.submitting.set(true);

    this.save()({
      hsnCode: value.hsnCode.trim(),
      gstPercentage: value.gstPercentage,
      description: value.description.trim() || null,
      active: value.active,
    }).subscribe({
      next: () => {
        this.submitting.set(false);
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
