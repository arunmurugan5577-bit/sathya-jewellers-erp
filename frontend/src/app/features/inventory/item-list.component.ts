import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';

import { Permissions } from '../../core/auth/permissions';
import { ConfirmService } from '../../core/services/confirm.service';
import { NotificationService } from '../../core/services/notification.service';
import { DataTableComponent } from '../../shared/components/data-table.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { PaginatorComponent } from '../../shared/components/paginator.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { InventoryFilters, InventoryItem } from '../../shared/models/inventory.model';
import { Lookup } from '../../shared/models/lookup.model';
import { DEFAULT_PAGE_SIZE, Page, emptyPage } from '../../shared/models/page.model';
import { WeightPipe } from '../../shared/pipes/weight.pipe';
import { CategoryService } from '../masters/category/category.service';
import { ItemTypeService } from '../masters/item-type/item-type.service';
import { SubCategoryService } from '../masters/sub-category/sub-category.service';
import { InventoryService } from './inventory.service';

/**
 * The stock list.
 *
 * Everything is done on the server: paging, sorting and all five filters. A shop
 * with fifty thousand pieces has to open this screen as fast as a shop with
 * fifty, which rules out fetching the stock and filtering it in the browser.
 *
 * The sub category filter cascades off the category filter, mirroring the form -
 * offering sub categories from other categories would produce filter
 * combinations that always return nothing.
 */
@Component({
  selector: 'app-item-list',
  standalone: true,
  imports: [
    RouterLink,
    PageHeaderComponent,
    DataTableComponent,
    PaginatorComponent,
    StatusBadgeComponent,
    HasPermissionDirective,
    WeightPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-header title="Inventory" subtitle="Every piece in the shop, one row each.">
        <button
          *appHasPermission="permissions.INVENTORY_CREATE"
          type="button"
          class="btn btn--primary"
          routerLink="/inventory/new"
        >
          Add item
        </button>
      </app-page-header>

      <section class="card">
        <div class="toolbar">
          <div class="toolbar__field">
            <label class="toolbar__label" for="serialNumber">Serial number</label>
            <input
              id="serialNumber"
              class="input serial"
              type="search"
              placeholder="e.g. 000123"
              inputmode="numeric"
              [value]="filters().serialNumber ?? ''"
              (input)="onSerialInput($event)"
            />
          </div>

          <div class="toolbar__field">
            <label class="toolbar__label" for="itemType">Item type</label>
            <select id="itemType" class="select" (change)="onFilterChange('itemTypeId', $event)">
              <option value="">All</option>
              @for (itemType of itemTypes(); track itemType.id) {
                <option [value]="itemType.id" [selected]="filters().itemTypeId === itemType.id">
                  {{ itemType.name }}
                </option>
              }
            </select>
          </div>

          <div class="toolbar__field">
            <label class="toolbar__label" for="category">Category</label>
            <select id="category" class="select" (change)="onCategoryChange($event)">
              <option value="">All</option>
              @for (category of categories(); track category.id) {
                <option [value]="category.id" [selected]="filters().categoryId === category.id">
                  {{ category.name }}
                </option>
              }
            </select>
          </div>

          <div class="toolbar__field">
            <label class="toolbar__label" for="subCategory">Sub category</label>
            <select
              id="subCategory"
              class="select"
              [disabled]="!filters().categoryId"
              (change)="onFilterChange('subCategoryId', $event)"
            >
              <option value="">{{ filters().categoryId ? 'All' : 'Select a category' }}</option>
              @for (subCategory of subCategories(); track subCategory.id) {
                <option
                  [value]="subCategory.id"
                  [selected]="filters().subCategoryId === subCategory.id"
                >
                  {{ subCategory.name }}
                </option>
              }
            </select>
          </div>

          <div class="toolbar__field">
            <label class="toolbar__label" for="status">Status</label>
            <select id="status" class="select" (change)="onStatusChange($event)">
              <option value="">All</option>
              <option value="true" [selected]="filters().active === true">Active</option>
              <option value="false" [selected]="filters().active === false">Inactive</option>
            </select>
          </div>

          <div class="toolbar__field">
            <label class="toolbar__label" for="saleStatus">Stock</label>
            <select id="saleStatus" class="select" (change)="onSaleStatusChange($event)">
              <option value="">All</option>
              <option value="AVAILABLE" [selected]="filters().status === 'AVAILABLE'">Available</option>
              <option value="SOLD" [selected]="filters().status === 'SOLD'">Sold</option>
            </select>
          </div>

          @if (hasFilters()) {
            <button type="button" class="btn btn--ghost" (click)="clearFilters()">Clear</button>
          }
        </div>

        <app-data-table
          [loading]="loading()"
          [isEmpty]="page().content.length === 0"
          [emptyTitle]="hasFilters() ? 'No items match these filters' : 'No inventory yet'"
          [emptyMessage]="
            hasFilters()
              ? 'Try widening the filters, or clear them to see everything.'
              : 'Add your first piece to start tracking stock.'
          "
        >
          <table class="table">
            <thead>
              <tr>
                <th class="th--sortable" (click)="toggleSort('serialNumber')">
                  Serial{{ sortIndicator('serialNumber') }}
                </th>
                <th>Item type</th>
                <th>Purity</th>
                <th>Category</th>
                <th>Sub category</th>
                <th>Size</th>
                <th class="td--numeric th--sortable" (click)="toggleSort('weightGrams')">
                  Weight{{ sortIndicator('weightGrams') }}
                </th>
                <th>Status</th>
                <th class="td--actions">Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (item of page().content; track item.id) {
                <tr>
                  <td>
                    <a class="table__serial" [routerLink]="['/inventory', item.id]">
                      {{ item.serialNumber }}
                    </a>
                  </td>
                  <td>{{ item.itemTypeName }}</td>
                  <td>{{ item.purityName }}</td>
                  <td>{{ item.categoryName }}</td>
                  <td>{{ item.subCategoryName || '-' }}</td>
                  <td>{{ item.size || '-' }}</td>
                  <td class="td--numeric">{{ item.weightGrams | weight }}</td>
                  <td>
                    @if (item.status === 'SOLD') {
                      <span class="badge badge--info">Sold</span>
                    } @else {
                      <app-status-badge [active]="item.active" activeLabel="In stock" />
                    }
                  </td>
                  <td class="td--actions">
                    <div class="row row--end">
                      <a class="btn btn--sm" [routerLink]="['/inventory', item.id]">View</a>
                      <a
                        *appHasPermission="permissions.INVENTORY_EDIT"
                        class="btn btn--sm"
                        [routerLink]="['/inventory', item.id, 'edit']"
                      >
                        Edit
                      </a>
                      <button
                        *appHasPermission="permissions.INVENTORY_EDIT"
                        type="button"
                        class="btn btn--sm"
                        (click)="toggleActive(item)"
                      >
                        {{ item.active ? 'Deactivate' : 'Activate' }}
                      </button>
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </app-data-table>

        <app-paginator
          [page]="page()"
          (pageChange)="reload($event)"
          (sizeChange)="onSizeChange($event)"
        />
      </section>
    </div>
  `,
  styles: [
    `
      .th--sortable {
        cursor: pointer;
        user-select: none;
      }

      .serial {
        font-family: var(--font-mono);
        letter-spacing: 0.06em;
      }
    `,
  ],
})
export class ItemListComponent {
  private readonly inventory = inject(InventoryService);
  private readonly itemTypeService = inject(ItemTypeService);
  private readonly categoryService = inject(CategoryService);
  private readonly subCategoryService = inject(SubCategoryService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly serialInput$ = new Subject<string>();

  protected readonly permissions = Permissions;
  protected readonly loading = signal(true);
  protected readonly page = signal<Page<InventoryItem>>(emptyPage<InventoryItem>());
  protected readonly filters = signal<InventoryFilters>({});
  protected readonly sortField = signal('createdAt');
  protected readonly sortDirection = signal<'asc' | 'desc'>('desc');

  protected readonly itemTypes = signal<Lookup[]>([]);
  protected readonly categories = signal<Lookup[]>([]);
  protected readonly subCategories = signal<Lookup[]>([]);

  constructor() {
    this.serialInput$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((serialNumber) => {
        this.filters.update((current) => ({ ...current, serialNumber: serialNumber || null }));
        this.reload(0);
      });

    this.itemTypeService.lookup().subscribe({ next: (values) => this.itemTypes.set(values) });
    this.categoryService.lookup().subscribe({ next: (values) => this.categories.set(values) });
    this.reload(0);
  }

  protected reload(page = this.page().page): void {
    this.loading.set(true);
    this.inventory
      .list(
        {
          page,
          size: this.page().size || DEFAULT_PAGE_SIZE,
          sort: this.sortField(),
          direction: this.sortDirection(),
        },
        this.filters(),
      )
      .subscribe({
        next: (result) => {
          this.page.set(result);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  protected onSerialInput(event: Event): void {
    this.serialInput$.next((event.target as HTMLInputElement).value.trim());
  }

  protected onFilterChange(key: 'itemTypeId' | 'subCategoryId', event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.filters.update((current) => ({ ...current, [key]: value === '' ? null : Number(value) }));
    this.reload(0);
  }

  /** Changing the category resets the sub category, which belongs to the old one. */
  protected onCategoryChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    const categoryId = value === '' ? null : Number(value);

    this.filters.update((current) => ({ ...current, categoryId, subCategoryId: null }));
    this.subCategories.set([]);

    if (categoryId !== null) {
      this.subCategoryService.lookupByCategory(categoryId).subscribe({
        next: (values) => this.subCategories.set(values),
      });
    }
    this.reload(0);
  }

  protected onStatusChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.filters.update((current) => ({
      ...current,
      active: value === '' ? null : value === 'true',
    }));
    this.reload(0);
  }

  protected onSaleStatusChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.filters.update((current) => ({
      ...current,
      status: value === 'AVAILABLE' || value === 'SOLD' ? value : null,
    }));
    this.reload(0);
  }

  protected onSizeChange(size: number): void {
    this.page.update((current) => ({ ...current, size }));
    this.reload(0);
  }

  protected toggleSort(field: string): void {
    if (this.sortField() === field) {
      this.sortDirection.update((direction) => (direction === 'asc' ? 'desc' : 'asc'));
    } else {
      this.sortField.set(field);
      this.sortDirection.set('asc');
    }
    this.reload(0);
  }

  protected sortIndicator(field: string): string {
    if (this.sortField() !== field) {
      return '';
    }
    return this.sortDirection() === 'asc' ? ' ↑' : ' ↓';
  }

  protected hasFilters(): boolean {
    const filters = this.filters();
    return Object.values(filters).some((value) => value !== null && value !== undefined && value !== '');
  }

  protected clearFilters(): void {
    this.filters.set({});
    this.subCategories.set([]);
    this.reload(0);
  }

  protected async toggleActive(item: InventoryItem): Promise<void> {
    if (item.active) {
      const confirmed = await this.confirm.ask({
        title: `Deactivate ${item.serialNumber}?`,
        message:
          'The piece stays on record with all its details, but is no longer counted as current ' +
          'stock. Use this when it leaves the shop.',
        confirmLabel: 'Deactivate',
        danger: true,
      });
      if (!confirmed) {
        return;
      }
    }

    this.inventory.setActive(item.id, !item.active).subscribe({
      next: () => {
        this.notifications.success(
          `Item ${item.serialNumber} ${item.active ? 'deactivated' : 'activated'}.`,
        );
        this.reload();
      },
    });
  }

}
