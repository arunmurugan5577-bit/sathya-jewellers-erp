import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { Permissions } from '../../../core/auth/permissions';
import { DataTableComponent } from '../../../shared/components/data-table.component';
import { PageHeaderComponent } from '../../../shared/components/page-header.component';
import { PaginatorComponent } from '../../../shared/components/paginator.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { ItemType, NamedMasterRequest } from '../../../shared/models/master.model';
import { MasterListPage } from '../shared/master-list.base';
import { NamedMasterFormComponent } from '../shared/named-master-form.component';
import { ItemTypeService } from './item-type.service';

/**
 * Item type master screen.
 *
 * Establishes the layout every master list uses: header with the create action,
 * a filter toolbar, a server-paginated table, and the form in a dialog so the
 * list stays in place behind it.
 */
@Component({
  selector: 'app-item-type-list',
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
        title="Item Types"
        subtitle="The materials you trade in. Purities are defined against these."
      >
        <button
          *appHasPermission="permissions.ITEM_TYPE_CREATE"
          type="button"
          class="btn btn--primary"
          (click)="openCreate()"
        >
          New item type
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
          [emptyTitle]="hasFilters() ? 'No item types match these filters' : 'No item types yet'"
          [emptyMessage]="
            hasFilters()
              ? 'Try a different search term or clear the filters.'
              : 'Add Gold, Silver or any other material you deal in.'
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
                <th class="td--numeric">Purities</th>
                <th>Status</th>
                <th class="td--actions">Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (itemType of page().content; track itemType.id) {
                <tr>
                  <td>{{ itemType.name }}</td>
                  <td><code>{{ itemType.code }}</code></td>
                  <td class="truncate">{{ itemType.description || '-' }}</td>
                  <td class="td--numeric">{{ itemType.purityCount }}</td>
                  <td><app-status-badge [active]="itemType.active" /></td>
                  <td class="td--actions">
                    <div class="row row--end">
                      <button
                        *appHasPermission="permissions.ITEM_TYPE_EDIT"
                        type="button"
                        class="btn btn--sm"
                        (click)="openEdit(itemType)"
                      >
                        Edit
                      </button>
                      <button
                        *appHasPermission="permissions.ITEM_TYPE_EDIT"
                        type="button"
                        class="btn btn--sm"
                        (click)="toggleActive(itemType)"
                      >
                        {{ itemType.active ? 'Deactivate' : 'Activate' }}
                      </button>
                      <button
                        *appHasPermission="permissions.ITEM_TYPE_DELETE"
                        type="button"
                        class="btn btn--sm btn--danger"
                        (click)="remove(itemType, itemType.name)"
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
        entityName="Item type"
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
export class ItemTypeListComponent extends MasterListPage<ItemType, NamedMasterRequest> {
  protected readonly service = inject(ItemTypeService);
  protected readonly entityName = 'Item type';
  protected readonly permissions = Permissions;

  constructor() {
    super();
    this.reload(0);
  }
}
