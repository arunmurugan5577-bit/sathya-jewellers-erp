import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';

import { Permissions } from '../../../core/auth/permissions';
import { QueryParams } from '../../../core/services/api.service';
import { DataTableComponent } from '../../../shared/components/data-table.component';
import { PageHeaderComponent } from '../../../shared/components/page-header.component';
import { PaginatorComponent } from '../../../shared/components/paginator.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { Lookup } from '../../../shared/models/lookup.model';
import { SubCategory, SubCategoryRequest } from '../../../shared/models/master.model';
import { CategoryService } from '../category/category.service';
import { MasterListPage } from '../shared/master-list.base';
import { SubCategoryFormComponent } from './sub-category-form.component';
import { SubCategoryService } from './sub-category.service';

/**
 * Sub category master screen.
 *
 * Adds a category filter to the standard master list, because sub categories are
 * only meaningful in the context of their parent - "show me everything under
 * Ring" is the question this screen is usually asked.
 */
@Component({
  selector: 'app-sub-category-list',
  standalone: true,
  imports: [
    PageHeaderComponent,
    DataTableComponent,
    PaginatorComponent,
    StatusBadgeComponent,
    HasPermissionDirective,
    SubCategoryFormComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-header
        title="Sub Categories"
        subtitle="Subdivisions of a category, such as Mens Ring under Ring."
      >
        <button
          *appHasPermission="permissions.SUB_CATEGORY_CREATE"
          type="button"
          class="btn btn--primary"
          (click)="openCreate()"
          [disabled]="categories().length === 0"
        >
          New sub category
        </button>
      </app-page-header>

      @if (categories().length === 0 && !loading()) {
        <div class="alert alert--info">
          There are no active categories yet. A sub category must belong to one, so add a category
          first.
        </div>
      }

      <section class="card">
        <div class="toolbar">
          <div class="toolbar__field">
            <label class="toolbar__label" for="search">Search</label>
            <input
              id="search"
              class="input"
              type="search"
              placeholder="Name, code or description"
              [value]="search()"
              (input)="onSearchInput($event)"
            />
          </div>

          <div class="toolbar__field">
            <label class="toolbar__label" for="category">Category</label>
            <select id="category" class="select" (change)="onCategoryChange($event)">
              <option value="">All categories</option>
              @for (category of categories(); track category.id) {
                <option [value]="category.id" [selected]="categoryFilter() === category.id">
                  {{ category.name }}
                </option>
              }
            </select>
          </div>

          <div class="toolbar__field">
            <label class="toolbar__label" for="status">Status</label>
            <select id="status" class="select" [value]="statusFilter()" (change)="onStatusChange($event)">
              <option value="">All</option>
              <option value="true">Active</option>
              <option value="false">Inactive</option>
            </select>
          </div>

          @if (hasAnyFilter()) {
            <button type="button" class="btn btn--ghost" (click)="clearAllFilters()">Clear</button>
          }
        </div>

        <app-data-table
          [loading]="loading()"
          [isEmpty]="page().content.length === 0"
          [emptyTitle]="hasAnyFilter() ? 'No sub categories match these filters' : 'No sub categories yet'"
          emptyMessage="Sub categories make the inventory list easier to read - Mens Ring, Kids Ring, and so on."
        >
          <table class="table">
            <thead>
              <tr>
                <th class="th--sortable" (click)="toggleSort('name')">Name{{ sortIndicator('name') }}</th>
                <th class="th--sortable" (click)="toggleSort('code')">Code{{ sortIndicator('code') }}</th>
                <th>Category</th>
                <th>Description</th>
                <th>Status</th>
                <th class="td--actions">Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (subCategory of page().content; track subCategory.id) {
                <tr>
                  <td>{{ subCategory.name }}</td>
                  <td><code>{{ subCategory.code }}</code></td>
                  <td>{{ subCategory.categoryName }}</td>
                  <td class="truncate">{{ subCategory.description || '-' }}</td>
                  <td><app-status-badge [active]="subCategory.active" /></td>
                  <td class="td--actions">
                    <div class="row row--end">
                      <button
                        *appHasPermission="permissions.SUB_CATEGORY_EDIT"
                        type="button"
                        class="btn btn--sm"
                        (click)="openEdit(subCategory)"
                      >
                        Edit
                      </button>
                      <button
                        *appHasPermission="permissions.SUB_CATEGORY_EDIT"
                        type="button"
                        class="btn btn--sm"
                        (click)="toggleActive(subCategory)"
                      >
                        {{ subCategory.active ? 'Deactivate' : 'Activate' }}
                      </button>
                      <button
                        *appHasPermission="permissions.SUB_CATEGORY_DELETE"
                        type="button"
                        class="btn btn--sm btn--danger"
                        (click)="remove(subCategory, subCategory.name)"
                      >
                        Delete
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
          (pageChange)="onPageChange($event)"
          (sizeChange)="onSizeChange($event)"
        />
      </section>
    </div>

    @if (dialogOpen()) {
      <app-sub-category-form
        [entity]="editing()"
        [categories]="categories()"
        [save]="saveHandler"
        (saved)="onSaved()"
        (cancelled)="closeDialog()"
      />
    }
  `,
  styles: [
    `
      .th--sortable {
        cursor: pointer;
        user-select: none;
      }

      code {
        font-family: var(--font-mono);
        font-size: var(--text-sm);
      }
    `,
  ],
})
export class SubCategoryListComponent extends MasterListPage<SubCategory, SubCategoryRequest> {
  protected readonly service = inject(SubCategoryService);
  protected readonly entityName = 'Sub category';
  protected readonly permissions = Permissions;

  private readonly categoryService = inject(CategoryService);

  protected readonly categories = signal<Lookup[]>([]);
  protected readonly categoryFilter = signal<number | null>(null);

  constructor() {
    super();
    this.categoryService.lookup().subscribe({
      next: (categories) => this.categories.set(categories),
    });
    this.reload(0);
  }

  protected override extraFilters(): QueryParams {
    return { categoryId: this.categoryFilter() };
  }

  protected onCategoryChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.categoryFilter.set(value === '' ? null : Number(value));
    this.reload(0);
  }

  protected hasAnyFilter(): boolean {
    return this.hasFilters() || this.categoryFilter() !== null;
  }

  protected clearAllFilters(): void {
    this.categoryFilter.set(null);
    this.clearFilters();
  }
}
