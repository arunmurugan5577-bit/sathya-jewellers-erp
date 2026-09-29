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
import { NamedMaster, NamedMasterRequest } from '../../../shared/models/master.model';
import { applyServerErrors, clearServerErrors } from '../../../shared/utils/form-errors';

/**
 * Create / edit dialog for the "name + code" masters.
 *
 * Item types and categories have identical fields and identical rules, so they
 * share one form. Duplicating it would mean that the day someone adds a hint to
 * the code field, only one of the two screens gets it.
 *
 * The parent supplies the save operation as a function rather than listening for
 * an event, which lets this component own the whole submit lifecycle - disabling
 * the button, projecting server field errors back onto the controls, and closing
 * only once the server has actually accepted the record.
 */
@Component({
  selector: 'app-named-master-form',
  standalone: true,
  imports: [ReactiveFormsModule, ModalComponent, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-modal [title]="dialogTitle()" (closed)="cancelled.emit()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate id="named-master-form">
        @if (formError()) {
          <div class="alert alert--error" role="alert">{{ formError() }}</div>
        }

        <div class="form-grid">
          <div class="field">
            <label class="field__label field__label--required" [attr.for]="'name'">
              {{ nameLabel() }}
            </label>
            <input
              id="name"
              class="input"
              formControlName="name"
              [class.input--invalid]="invalid('name')"
              [attr.aria-invalid]="invalid('name')"
              autocomplete="off"
            />
            <app-field-error [control]="form.controls.name" [label]="nameLabel()" />
          </div>

          <div class="field">
            <label class="field__label field__label--required" for="code">Code</label>
            <input
              id="code"
              class="input"
              formControlName="code"
              [class.input--invalid]="invalid('code')"
              [attr.aria-invalid]="invalid('code')"
              autocomplete="off"
              autocapitalize="characters"
            />
            <p class="field__hint">Short unique code, stored in upper case.</p>
            <app-field-error [control]="form.controls.code" label="Code" />
          </div>

          <div class="field field--full">
            <label class="field__label" for="description">Description</label>
            <textarea id="description" class="textarea" formControlName="description"></textarea>
            <app-field-error [control]="form.controls.description" label="Description" />
          </div>

          <div class="field field--full">
            <label class="checkbox">
              <input type="checkbox" formControlName="active" />
              <span>Active - available for selection on other screens</span>
            </label>
          </div>
        </div>
      </form>

      <ng-container modalActions>
        <button type="button" class="btn" (click)="cancelled.emit()" [disabled]="submitting()">
          Cancel
        </button>
        <button
          type="submit"
          form="named-master-form"
          class="btn btn--primary"
          [disabled]="submitting()"
        >
          {{ submitting() ? 'Saving...' : 'Save' }}
        </button>
      </ng-container>
    </app-modal>
  `,
})
export class NamedMasterFormComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);

  /** Singular entity name, e.g. "Item type". Used in the dialog title. */
  readonly entityName = input.required<string>();
  readonly nameLabel = input('Name');

  /** The record being edited, or null to create a new one. */
  readonly entity = input<NamedMaster | null>(null);

  /** Performs the save. Returning an Observable lets the form own the lifecycle. */
  readonly save = input.required<(request: NamedMasterRequest) => Observable<unknown>>();

  readonly saved = output<void>();
  readonly cancelled = output<void>();

  protected readonly submitting = signal(false);
  protected readonly formError = signal('');

  protected readonly form = this.formBuilder.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    code: [
      '',
      [Validators.required, Validators.maxLength(20), Validators.pattern(/^[A-Za-z0-9_-]+$/)],
    ],
    description: ['', Validators.maxLength(500)],
    active: [true],
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
        name: entity.name,
        code: entity.code,
        description: entity.description ?? '',
        active: entity.active,
      });
    }
  }

  protected dialogTitle(): string {
    return this.entity() ? `Edit ${this.entityName().toLowerCase()}` : `New ${this.entityName().toLowerCase()}`;
  }

  protected invalid(control: 'name' | 'code'): boolean {
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
      name: value.name.trim(),
      code: value.code.trim().toUpperCase(),
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
