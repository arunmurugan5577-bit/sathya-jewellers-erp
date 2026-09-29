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
import { SubCategory, SubCategoryRequest } from '../../../shared/models/master.model';
import { applyServerErrors, clearServerErrors } from '../../../shared/utils/form-errors';

/**
 * Create / edit dialog for a sub category.
 *
 * The parent category is mandatory here exactly as it is on the server. The
 * dropdown lists active categories only - the API's lookup endpoint returns
 * nothing else - so an inactive parent cannot be chosen by accident.
 */
@Component({
  selector: 'app-sub-category-form',
  standalone: true,
  imports: [ReactiveFormsModule, ModalComponent, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-modal [title]="entity() ? 'Edit sub category' : 'New sub category'" (closed)="cancelled.emit()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate id="sub-category-form">
        @if (formError()) {
          <div class="alert alert--error" role="alert">{{ formError() }}</div>
        }

        <div class="form-grid">
          <div class="field field--full">
            <label class="field__label field__label--required" for="categoryId">Category</label>
            <select
              id="categoryId"
              class="select"
              formControlName="categoryId"
              [class.select--invalid]="invalid('categoryId')"
            >
              <option [ngValue]="null" disabled>Select a category</option>
              @for (category of categories(); track category.id) {
                <option [ngValue]="category.id">{{ category.name }}</option>
              }
            </select>
            <app-field-error [control]="form.controls.categoryId" label="Category" />
          </div>

          <div class="field">
            <label class="field__label field__label--required" for="name">Name</label>
            <input
              id="name"
              class="input"
              formControlName="name"
              [class.input--invalid]="invalid('name')"
              autocomplete="off"
            />
            <p class="field__hint">Unique within the selected category.</p>
            <app-field-error [control]="form.controls.name" label="Name" />
          </div>

          <div class="field">
            <label class="field__label field__label--required" for="code">Code</label>
            <input
              id="code"
              class="input"
              formControlName="code"
              [class.input--invalid]="invalid('code')"
              autocomplete="off"
              autocapitalize="characters"
            />
            <p class="field__hint">Unique across the whole shop.</p>
            <app-field-error [control]="form.controls.code" label="Code" />
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
        <button type="submit" form="sub-category-form" class="btn btn--primary" [disabled]="submitting()">
          {{ submitting() ? 'Saving...' : 'Save' }}
        </button>
      </ng-container>
    </app-modal>
  `,
})
export class SubCategoryFormComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);

  readonly entity = input<SubCategory | null>(null);
  readonly categories = input.required<Lookup[]>();
  readonly save = input.required<(request: SubCategoryRequest) => Observable<unknown>>();

  readonly saved = output<void>();
  readonly cancelled = output<void>();

  protected readonly submitting = signal(false);
  protected readonly formError = signal('');

  protected readonly form = this.formBuilder.group({
    categoryId: this.formBuilder.control<number | null>(null, Validators.required),
    name: this.formBuilder.nonNullable.control('', [Validators.required, Validators.maxLength(100)]),
    code: this.formBuilder.nonNullable.control('', [
      Validators.required,
      Validators.maxLength(20),
      Validators.pattern(/^[A-Za-z0-9_-]+$/),
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
        categoryId: entity.categoryId,
        name: entity.name,
        code: entity.code,
        description: entity.description ?? '',
        active: entity.active,
      });
    }
  }

  protected invalid(control: 'categoryId' | 'name' | 'code'): boolean {
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
      categoryId: value.categoryId,
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
