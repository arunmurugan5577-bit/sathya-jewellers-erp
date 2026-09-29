import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { NotificationService } from '../../core/services/notification.service';
import { FieldErrorComponent } from '../../shared/components/field-error.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { SpinnerComponent } from '../../shared/components/spinner.component';
import { Lookup } from '../../shared/models/lookup.model';
import { applyServerErrors, clearServerErrors } from '../../shared/utils/form-errors';
import { CategoryService } from '../masters/category/category.service';
import { HsnService } from '../masters/hsn/hsn.service';
import { ItemTypeService } from '../masters/item-type/item-type.service';
import { PurityService } from '../masters/purity/purity.service';
import { SubCategoryService } from '../masters/sub-category/sub-category.service';
import { InventoryService } from './inventory.service';

/**
 * Add / edit a jewellery piece.
 *
 * A full page rather than a dialog: it has ten fields and two cascading
 * dropdowns, and stock-in is a task somebody sits down to do rather than
 * something they do while reading a list.
 *
 * The two cascades are the heart of it. Changing the item type reloads the
 * purity list and clears the current selection; changing the category does the
 * same for sub categories. Clearing is the important half - leaving a 22K gold
 * purity selected after switching to silver would produce a request the server
 * rejects, and the user would have to work out why.
 */
@Component({
  selector: 'app-item-form',
  standalone: true,
  imports: [ReactiveFormsModule, PageHeaderComponent, FieldErrorComponent, SpinnerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-header
        [title]="isEdit() ? 'Edit item' : 'Add inventory item'"
        [subtitle]="
          isEdit()
            ? 'Changing an item does not change its history.'
            : 'Each row is one physical piece, identified by its serial number.'
        "
      >
        <button type="button" class="btn" (click)="cancel()">Cancel</button>
      </app-page-header>

      @if (loading()) {
        <app-spinner label="Loading..." />
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          @if (formError()) {
            <div class="alert alert--error" role="alert">{{ formError() }}</div>
          }

          <section class="card">
            <div class="card__header"><h2 class="card__title">Identification</h2></div>
            <div class="card__body">
              <div class="form-grid">
                <div class="field">
                  <label class="field__label field__label--required" for="serialNumber">
                    Serial number
                  </label>
                  <div class="row">
                    <input
                      id="serialNumber"
                      class="input serial"
                      formControlName="serialNumber"
                      inputmode="numeric"
                      maxlength="6"
                      [class.input--invalid]="invalid('serialNumber')"
                    />
                    @if (!isEdit()) {
                      <button type="button" class="btn btn--sm" (click)="suggestSerial()">
                        Suggest
                      </button>
                    }
                  </div>
                  <p class="field__hint">Exactly 6 digits. Leading zeros are kept.</p>
                  <app-field-error [control]="form.controls.serialNumber" label="Serial number" />
                </div>
              </div>
            </div>
          </section>

          <section class="card">
            <div class="card__header"><h2 class="card__title">Material</h2></div>
            <div class="card__body">
              <div class="form-grid">
                <div class="field">
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
                  <label class="field__label field__label--required" for="purityId">Purity</label>
                  <select
                    id="purityId"
                    class="select"
                    formControlName="purityId"
                    [class.select--invalid]="invalid('purityId')"
                  >
                    <option [ngValue]="null" disabled>
                      {{ form.controls.itemTypeId.value ? 'Select a purity' : 'Select an item type first' }}
                    </option>
                    @for (purity of purities(); track purity.id) {
                      <option [ngValue]="purity.id">{{ purity.name }}</option>
                    }
                  </select>
                  @if (form.controls.itemTypeId.value && purities().length === 0 && !loadingPurities()) {
                    <p class="field__hint">
                      This item type has no active purities. Add one on the Purities screen.
                    </p>
                  }
                  <app-field-error [control]="form.controls.purityId" label="Purity" />
                </div>
              </div>
            </div>
          </section>

          <section class="card">
            <div class="card__header"><h2 class="card__title">Classification</h2></div>
            <div class="card__body">
              <div class="form-grid">
                <div class="field">
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
                  <label class="field__label" for="subCategoryId">Sub category</label>
                  <select id="subCategoryId" class="select" formControlName="subCategoryId">
                    <option [ngValue]="null">
                      {{ form.controls.categoryId.value ? 'None' : 'Select a category first' }}
                    </option>
                    @for (subCategory of subCategories(); track subCategory.id) {
                      <option [ngValue]="subCategory.id">{{ subCategory.name }}</option>
                    }
                  </select>
                  <p class="field__hint">Optional.</p>
                </div>

                <div class="field">
                  <label class="field__label" for="hsnId">HSN code</label>
                  <select id="hsnId" class="select" formControlName="hsnId">
                    <option [ngValue]="null">None</option>
                    @for (hsn of hsnCodes(); track hsn.id) {
                      <option [ngValue]="hsn.id">{{ hsn.name }}</option>
                    }
                  </select>
                  <p class="field__hint">Optional now; required before the piece can be billed.</p>
                </div>

                <div class="field">
                  <label class="field__label" for="size">Size</label>
                  <input id="size" class="input" formControlName="size" placeholder="16, Medium, 18 inch" />
                  <p class="field__hint">Free text - a ring size, a length, or a label.</p>
                </div>
                <div class="field">
                  <label class="field__label field__label--required" for="weightGrams">
                    Weight (grams)
                  </label>
                  <input
                    id="weightGrams"
                    class="input"
                    type="number"
                    step="0.001"
                    min="0.001"
                    formControlName="weightGrams"
                    [class.input--invalid]="invalid('weightGrams')"
                  />
                  <p class="field__hint">Gross weight, to the milligram.</p>
                  <app-field-error [control]="form.controls.weightGrams" label="Weight" />
                </div>

                <div class="field field--full">
                  <label class="field__label" for="description">Description</label>
                  <textarea id="description" class="textarea" formControlName="description"></textarea>
                </div>

                <div class="field field--full">
                  <label class="checkbox">
                    <input type="checkbox" formControlName="active" />
                    <span>Active - counted as part of current stock</span>
                  </label>
                </div>
              </div>
            </div>
          </section>

          <div class="row row--end">
            <button type="button" class="btn" (click)="cancel()" [disabled]="submitting()">
              Cancel
            </button>
            <button type="submit" class="btn btn--primary" [disabled]="submitting()">
              {{ submitting() ? 'Saving...' : isEdit() ? 'Save changes' : 'Add item' }}
            </button>
          </div>
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

      .serial {
        font-family: var(--font-mono);
        letter-spacing: 0.08em;
      }
    `,
  ],
})
export class ItemFormComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly inventory = inject(InventoryService);
  private readonly itemTypeService = inject(ItemTypeService);
  private readonly purityService = inject(PurityService);
  private readonly categoryService = inject(CategoryService);
  private readonly subCategoryService = inject(SubCategoryService);
  private readonly hsnService = inject(HsnService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  /** Present on the edit route only; bound from the URL. */
  readonly id = input<string>();

  protected readonly isEdit = computed(() => this.id() !== undefined);
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly loadingPurities = signal(false);
  protected readonly formError = signal('');

  protected readonly itemTypes = signal<Lookup[]>([]);
  protected readonly purities = signal<Lookup[]>([]);
  protected readonly categories = signal<Lookup[]>([]);
  protected readonly subCategories = signal<Lookup[]>([]);
  protected readonly hsnCodes = signal<Lookup[]>([]);

  protected readonly form = this.formBuilder.group({
    serialNumber: this.formBuilder.nonNullable.control('', [
      Validators.required,
      Validators.pattern(/^[0-9]{6}$/),
    ]),
    itemTypeId: this.formBuilder.control<number | null>(null, Validators.required),
    purityId: this.formBuilder.control<number | null>(null, Validators.required),
    categoryId: this.formBuilder.control<number | null>(null, Validators.required),
    subCategoryId: this.formBuilder.control<number | null>(null),
    hsnId: this.formBuilder.control<number | null>(null),
    size: this.formBuilder.nonNullable.control('', Validators.maxLength(50)),
    weightGrams: this.formBuilder.control<number | null>(null, [
      Validators.required,
      Validators.min(0.001),
    ]),
    description: this.formBuilder.nonNullable.control('', Validators.maxLength(1000)),
    active: this.formBuilder.nonNullable.control(true),
  });

  constructor() {
    this.wireCascades();

    effect(() => {
      const id = this.id();
      if (id) {
        this.loadItem(Number(id));
      }
    });
  }

  /**
   * In ngOnInit rather than the constructor: `id` is bound from the route after
   * construction, so the create-versus-edit decision can only be made here.
   */
  ngOnInit(): void {
    this.loadLookups();
  }

  // --------------------------------------------------------------- setup ---

  private loadLookups(): void {
    this.itemTypeService.lookup().subscribe({ next: (values) => this.itemTypes.set(values) });
    this.categoryService.lookup().subscribe({ next: (values) => this.categories.set(values) });
    this.hsnService.lookup().subscribe({ next: (values) => this.hsnCodes.set(values) });

    if (!this.isEdit()) {
      this.loading.set(false);
      this.suggestSerial();
    }
  }

  /**
   * Wires the two dependent dropdowns.
   *
   * Clearing the dependent value is not optional: keeping it would let the user
   * submit a combination the server refuses, and the message they would get back
   * ("purity belongs to Silver") would be about a field they did not touch.
   */
  private wireCascades(): void {
    this.form.controls.itemTypeId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((itemTypeId) => {
        this.form.controls.purityId.setValue(null, { emitEvent: false });
        this.purities.set([]);
        if (itemTypeId !== null) {
          this.loadPurities(itemTypeId);
        }
      });

    this.form.controls.categoryId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((categoryId) => {
        this.form.controls.subCategoryId.setValue(null, { emitEvent: false });
        this.subCategories.set([]);
        if (categoryId !== null) {
          this.loadSubCategories(categoryId);
        }
      });
  }

  private loadPurities(itemTypeId: number, selected?: number | null): void {
    this.loadingPurities.set(true);
    this.purityService.lookupByItemType(itemTypeId).subscribe({
      next: (values) => {
        this.purities.set(values);
        this.loadingPurities.set(false);
        if (selected != null && values.some((value) => value.id === selected)) {
          this.form.controls.purityId.setValue(selected, { emitEvent: false });
        }
      },
      error: () => this.loadingPurities.set(false),
    });
  }

  private loadSubCategories(categoryId: number, selected?: number | null): void {
    this.subCategoryService.lookupByCategory(categoryId).subscribe({
      next: (values) => {
        this.subCategories.set(values);
        if (selected != null && values.some((value) => value.id === selected)) {
          this.form.controls.subCategoryId.setValue(selected, { emitEvent: false });
        }
      },
    });
  }

  private loadItem(id: number): void {
    this.loading.set(true);
    this.inventory.get(id).subscribe({
      next: (item) => {
        // Patch without events so the cascade wiring does not immediately clear
        // the purity and sub category that came back from the server; both are
        // restored by the lookup calls below once their lists have loaded.
        this.form.patchValue(
          {
            serialNumber: item.serialNumber,
            itemTypeId: item.itemTypeId,
            categoryId: item.categoryId,
            size: item.size ?? '',
            weightGrams: item.weightGrams,
            description: item.description ?? '',
            active: item.active,
            hsnId: item.hsnId ?? null,
          },
          { emitEvent: false },
        );

        this.loadPurities(item.itemTypeId, item.purityId);
        this.loadSubCategories(item.categoryId, item.subCategoryId ?? null);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        void this.router.navigate(['/inventory']);
      },
    });
  }

  // -------------------------------------------------------------- actions ---

  protected suggestSerial(): void {
    this.inventory.nextSerialNumber().subscribe({
      next: (serialNumber) => this.form.controls.serialNumber.setValue(serialNumber),
    });
  }

  protected invalid(
    control: 'serialNumber' | 'itemTypeId' | 'purityId' | 'categoryId' | 'weightGrams',
  ): boolean {
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
      this.formError.set('Some fields need attention before this item can be saved.');
      return;
    }

    const value = this.form.getRawValue();
    const request = {
      serialNumber: value.serialNumber.trim(),
      itemTypeId: value.itemTypeId,
      purityId: value.purityId,
      categoryId: value.categoryId,
      subCategoryId: value.subCategoryId,
      hsnId: value.hsnId,
      size: value.size.trim() || null,
      weightGrams: value.weightGrams,
      description: value.description.trim() || null,
      active: value.active,
    };

    this.submitting.set(true);
    const id = this.id();
    const request$ = id
      ? this.inventory.update(Number(id), request)
      : this.inventory.create(request);

    request$.subscribe({
      next: (item) => {
        this.submitting.set(false);
        this.notifications.success(
          id ? `Item ${item.serialNumber} updated.` : `Item ${item.serialNumber} added.`,
        );
        void this.router.navigate(['/inventory', item.id]);
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        const unmatched = applyServerErrors(this.form, error);
        this.formError.set(
          unmatched.length > 0 ? unmatched.join(' ') : 'This item could not be saved.',
        );
      },
    });
  }

  protected cancel(): void {
    void this.router.navigate(['/inventory']);
  }
}
