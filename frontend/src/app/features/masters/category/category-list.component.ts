import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { Permissions } from '../../../core/auth/permissions';
import { DataTableComponent } from '../../../shared/components/data-table.component';
import { PageHeaderComponent } from '../../../shared/components/page-header.component';
import { PaginatorComponent } from '../../../shared/components/paginator.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { Category, NamedMasterRequest } from '../../../shared/models/master.model';
import { MasterListPage } from '../shared/master-list.base';
import { NamedMasterFormComponent } from '../shared/named-master-form.component';
import { CategoryService } from './category.service';

/**
 * Category master screen.
 *
 * Same shape as the item type screen - the two masters are structurally
 * identical, so they share both the list behaviour and the form component.
 */
@Component({
  selector: 'app-category-list',
  standalone: true,
  imports: [
    PageHeaderComponent,
    DataTableComponent,
    PaginatorComponent,
    StatusBadgeComponent,
    HasPermissionDirective,
    NamedMasterFormComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-header
        title="Categories"
        subtitle="The kinds of ornament you stock. Sub categories hang off these."
      >
        <button
          *appHasPermission="permissions.CATEGORY_CREATE"
          type="button"
          class="btn btn--primary"
          (click)="openCreate()"
        >
          New category
        </button>
      </app-page-header>

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
            <label class="toolbar__label" for="status">Status</label>
            <select id="status" class="select" [value]="statusFilter()" (change)="onStatusChange($event)">
              <option value="">All</option>
              <option value="true">Active</option>
              <option value="false">Inactive</option>
            </select>
          </div>

          @if (hasFilters()) {
            <button type="button" class="btn btn--ghost" (click)="clearFilters()">Clear</button>
          }
        </div>

        <app-data-table
          [loading]="loading()"
          [isEmpty]="page().content.length === 0"
          [emptyTitle]="hasFilters() ? 'No categories match these filters' : 'No categories yet'"
          [emptyMessage]="
            hasFilters()
              ? 'Try a different search term or clear the filters.'
              : 'Add Ring, Chain, Bangle and the other kinds you stock.'
          "
        >
          <table class="table">
            <thead>
              <tr>
                <th class="th--sortable" (click)="toggleSort('name')">
                  Name{{ sortIndicator('name') }}
                </th>
                <th class="th--sortable" (click)="toggleSort('code')">Code{{ sortIndicator('code') }}</th>
                <th>Description</th>
                <th class="td--numeric">Sub categories</th>
                <th>Status</th>
                <th class="td--actions">Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (category of page().content; track category.id) {
                <tr>
                  <td>{{ category.name }}</td>
                  <td><code>{{ category.code }}</code></td>
                  <td class="truncate">{{ category.description || '-' }}</td>
                  <td class="td--numeric">{{ category.subCategoryCount }}</td>
                  <td><app-status-badge [active]="category.active" /></td>
                  <td class="td--actions">
                    <div class="row row--end">
                      <button
                        *appHasPermission="permissions.CATEGORY_EDIT"
                        type="button"
                        class="btn btn--sm"
                        (click)="openEdit(category)"
                      >
                        Edit
                      </button>
                      <button
                        *appHasPermission="permissions.CATEGORY_EDIT"
                        type="button"
                        class="btn btn--sm"
                        (click)="toggleActive(category)"
                      >
                        {{ category.active ? 'Deactivate' : 'Activate' }}
                      </button>
                      <button
                        *appHasPermission="permissions.CATEGORY_DELETE"
                        type="button"
                        class="btn btn--sm btn--danger"
                        (click)="remove(category, category.name)"
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
      <app-named-master-form
        entityName="Category"
        [entity]="editing()"
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

      .th--sortable:hover {
        color: var(--text-primary);
      }

      code {
        font-family: var(--font-mono);
        font-size: var(--text-sm);
      }
    `,
  ],
})
export class CategoryListComponent extends MasterListPage<Category, NamedMasterRequest> {
  protected readonly service = inject(CategoryService);
  protected readonly entityName = 'Category';
  protected readonly permissions = Permissions;

  constructor() {
    super();
    this.reload(0);
  }
}
