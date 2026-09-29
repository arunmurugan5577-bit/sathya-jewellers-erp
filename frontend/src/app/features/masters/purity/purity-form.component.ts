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
import { Lookup } from '../../../shared/models/lookup.model';
import { Purity, PurityRequest } from '../../../shared/models/master.model';
import { applyServerErrors, clearServerErrors } from '../../../shared/utils/form-errors';

/**
 * Create / edit dialog for a purity.
 *
 * A purity belongs to exactly one item type, and the server enforces that on
 * every inventory item. Choosing the item type here is what makes the cascading
 * purity dropdown on the inventory form possible.
 */
@Component({
  selector: 'app-purity-form',
  standalone: true,
  imports: [ReactiveFormsModule, ModalComponent, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-modal [title]="entity() ? 'Edit purity' : 'New purity'" (closed)="cancelled.emit()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate id="purity-form">
        @if (formError()) {
          <div class="alert alert--error" role="alert">{{ formError() }}</div>
        }

        <div class="form-grid">
          <div class="field field--full">
            <label class="field__label field__label--required" for="itemTypeId">Item type</label>
            <select
              id="itemTypeId"
              class="select"
              formControlName="itemTypeId"
              [class.select--invalid]="invalid('itemTypeId')"
            >
              <option [ngValue]="null" disabled>Select an item type</option>
              @for (itemType of itemTypes(); track itemType.id) {
                <option [ngValue]="itemType.id">{{ itemType.name }}</option>
              }
            </select>
            <app-field-error [control]="form.controls.itemTypeId" label="Item type" />
          </div>

          <div class="field">
            <label class="field__label field__label--required" for="name">Name</label>
            <input
              id="name"
              class="input"
              formControlName="name"
              placeholder="22K / 916"
              [class.input--invalid]="invalid('name')"
              autocomplete="off"
            />
            <app-field-error [control]="form.controls.name" label="Name" />
          </div>

          <div class="field">
            <label class="field__label field__label--required" for="purityValue">
              Fineness
            </label>
            <input
              id="purityValue"
              class="input"
              type="number"
              step="0.001"
              min="0.001"
              max="999.999"
              formControlName="purityValue"
              [class.input--invalid]="invalid('purityValue')"
            />
            <p class="field__hint">Parts per thousand - 916 for 22K gold, 925 for sterling silver.</p>
            <app-field-error [control]="form.controls.purityValue" label="Fineness" />
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
        <button type="submit" form="purity-form" class="btn btn--primary" [disabled]="submitting()">
          {{ submitting() ? 'Saving...' : 'Save' }}
        </button>
      </ng-container>
    </app-modal>
  `,
})
export class PurityFormComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);

  readonly entity = input<Purity | null>(null);
  readonly itemTypes = input.required<Lookup[]>();
  readonly save = input.required<(request: PurityRequest) => Observable<unknown>>();

  readonly saved = output<void>();
  readonly cancelled = output<void>();

  protected readonly submitting = signal(false);
  protected readonly formError = signal('');

  protected readonly form = this.formBuilder.group({
    itemTypeId: this.formBuilder.control<number | null>(null, Validators.required),
    name: this.formBuilder.nonNullable.control('', [Validators.required, Validators.maxLength(50)]),
    purityValue: this.formBuilder.control<number | null>(null, [
      Validators.required,
      Validators.min(0.001),
      Validators.max(999.999),
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
        itemTypeId: entity.itemTypeId,
        name: entity.name,
        purityValue: entity.purityValue,
        description: entity.description ?? '',
        active: entity.active,
      });
    }
  }

  protected invalid(control: 'itemTypeId' | 'name' | 'purityValue'): boolean {
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
      itemTypeId: value.itemTypeId,
      name: value.name.trim(),
      purityValue: value.purityValue,
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
