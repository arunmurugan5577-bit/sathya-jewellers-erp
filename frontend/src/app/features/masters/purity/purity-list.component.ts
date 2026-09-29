import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';

import { Permissions } from '../../../core/auth/permissions';
import { QueryParams } from '../../../core/services/api.service';
import { DataTableComponent } from '../../../shared/components/data-table.component';
import { PageHeaderComponent } from '../../../shared/components/page-header.component';
import { PaginatorComponent } from '../../../shared/components/paginator.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { Lookup } from '../../../shared/models/lookup.model';
import { Purity, PurityRequest } from '../../../shared/models/master.model';
import { ItemTypeService } from '../item-type/item-type.service';
import { MasterListPage } from '../shared/master-list.base';
import { PurityFormComponent } from './purity-form.component';
import { PurityService } from './purity.service';

/**
 * Purity master screen.
 *
 * Filters by item type, because that is how purities are actually read: "what
 * finenesses do we carry in gold" is the question, not "list every purity".
 */
@Component({
  selector: 'app-purity-list',
  standalone: true,
  imports: [
    PageHeaderComponent,
    DataTableComponent,
    PaginatorComponent,
    StatusBadgeComponent,
    HasPermissionDirective,
    PurityFormComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-header
        title="Purities"
        subtitle="Fineness values, each belonging to one item type."
      >
        <button
          *appHasPermission="permissions.PURITY_CREATE"
          type="button"
          class="btn btn--primary"
          (click)="openCreate()"
          [disabled]="itemTypes().length === 0"
        >
          New purity
        </button>
      </app-page-header>

      @if (itemTypes().length === 0 && !loading()) {
        <div class="alert alert--info">
          There are no active item types yet. A purity must belong to one, so add an item type
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
              placeholder="Name or description"
              [value]="search()"
              (input)="onSearchInput($event)"
            />
          </div>

          <div class="toolbar__field">
            <label class="toolbar__label" for="itemType">Item type</label>
            <select id="itemType" class="select" (change)="onItemTypeChange($event)">
              <option value="">All item types</option>
              @for (itemType of itemTypes(); track itemType.id) {
                <option [value]="itemType.id" [selected]="itemTypeFilter() === itemType.id">
                  {{ itemType.name }}
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
          [emptyTitle]="hasAnyFilter() ? 'No purities match these filters' : 'No purities yet'"
          emptyMessage="Add the finenesses you trade in - 916 and 750 for gold, 925 for silver."
        >
          <table class="table">
            <thead>
              <tr>
                <th class="th--sortable" (click)="toggleSort('name')">Name{{ sortIndicator('name') }}</th>
                <th>Item type</th>
                <th class="td--numeric th--sortable" (click)="toggleSort('purityValue')">
                  Fineness{{ sortIndicator('purityValue') }}
                </th>
                <th>Description</th>
                <th>Status</th>
                <th class="td--actions">Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (purity of page().content; track purity.id) {
                <tr>
                  <td>{{ purity.name }}</td>
                  <td>{{ purity.itemTypeName }}</td>
                  <td class="td--numeric">{{ purity.purityValue }}</td>
                  <td class="truncate">{{ purity.description || '-' }}</td>
                  <td><app-status-badge [active]="purity.active" /></td>
                  <td class="td--actions">
                    <div class="row row--end">
                      <button
                        *appHasPermission="permissions.PURITY_EDIT"
                        type="button"
                        class="btn btn--sm"
                        (click)="openEdit(purity)"
                      >
                        Edit
                      </button>
                      <button
                        *appHasPermission="permissions.PURITY_EDIT"
                        type="button"
                        class="btn btn--sm"
                        (click)="toggleActive(purity)"
                      >
                        {{ purity.active ? 'Deactivate' : 'Activate' }}
                      </button>
                      <button
                        *appHasPermission="permissions.PURITY_DELETE"
                        type="button"
                        class="btn btn--sm btn--danger"
                        (click)="remove(purity, purity.name)"
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
      <app-purity-form
        [entity]="editing()"
        [itemTypes]="itemTypes()"
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
    `,
  ],
})
export class PurityListComponent extends MasterListPage<Purity, PurityRequest> {
  protected readonly service = inject(PurityService);
  protected readonly entityName = 'Purity';
  protected readonly permissions = Permissions;

  private readonly itemTypeService = inject(ItemTypeService);

  protected readonly itemTypes = signal<Lookup[]>([]);
  protected readonly itemTypeFilter = signal<number | null>(null);

  constructor() {
    super();
    // Highest fineness first: 24K above 22K above 18K is how a jeweller reads it.
    this.sortField.set('purityValue');
    this.sortDirection.set('desc');

    this.itemTypeService.lookup().subscribe({
      next: (itemTypes) => this.itemTypes.set(itemTypes),
    });
    this.reload(0);
  }

  protected override extraFilters(): QueryParams {
    return { itemTypeId: this.itemTypeFilter() };
  }

  protected onItemTypeChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.itemTypeFilter.set(value === '' ? null : Number(value));
    this.reload(0);
  }

  protected hasAnyFilter(): boolean {
    return this.hasFilters() || this.itemTypeFilter() !== null;
  }

  protected clearAllFilters(): void {
    this.itemTypeFilter.set(null);
    this.clearFilters();
  }
}
