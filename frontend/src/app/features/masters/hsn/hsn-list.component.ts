import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { Permissions } from '../../../core/auth/permissions';
import { DataTableComponent } from '../../../shared/components/data-table.component';
import { PageHeaderComponent } from '../../../shared/components/page-header.component';
import { PaginatorComponent } from '../../../shared/components/paginator.component';
import { StatusBadgeComponent } from '../../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { HsnCode, HsnCodeRequest } from '../../../shared/models/master.model';
import { PercentagePipe } from '../../../shared/pipes/percentage.pipe';
import { MasterListPage } from '../shared/master-list.base';
import { HsnFormComponent } from './hsn-form.component';
import { HsnService } from './hsn.service';

/** HSN code master screen. */
@Component({
  selector: 'app-hsn-list',
  standalone: true,
  imports: [
    PageHeaderComponent,
    DataTableComponent,
    PaginatorComponent,
    StatusBadgeComponent,
    HasPermissionDirective,
    HsnFormComponent,
    PercentagePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-header
        title="HSN Codes"
        subtitle="Tax classification codes and the GST rate that applies to each."
      >
        <button
          *appHasPermission="permissions.HSN_CREATE"
          type="button"
          class="btn btn--primary"
          (click)="openCreate()"
        >
          New HSN code
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
              placeholder="Code or description"
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
          [emptyTitle]="hasFilters() ? 'No HSN codes match these filters' : 'No HSN codes yet'"
          emptyMessage="HSN codes carry the GST rate that will be applied when a piece is billed."
        >
          <table class="table">
            <thead>
              <tr>
                <th class="th--sortable" (click)="toggleSort('hsnCode')">
                  Code{{ sortIndicator('hsnCode') }}
                </th>
                <th>Description</th>
                <th class="td--numeric th--sortable" (click)="toggleSort('gstPercentage')">
                  GST{{ sortIndicator('gstPercentage') }}
                </th>
                <th>Status</th>
                <th class="td--actions">Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (hsn of page().content; track hsn.id) {
                <tr>
                  <td><code>{{ hsn.hsnCode }}</code></td>
                  <td class="truncate">{{ hsn.description || '-' }}</td>
                  <td class="td--numeric">{{ hsn.gstPercentage | percentage }}</td>
                  <td><app-status-badge [active]="hsn.active" /></td>
                  <td class="td--actions">
                    <div class="row row--end">
                      <button
                        *appHasPermission="permissions.HSN_EDIT"
                        type="button"
                        class="btn btn--sm"
                        (click)="openEdit(hsn)"
                      >
                        Edit
                      </button>
                      <button
                        *appHasPermission="permissions.HSN_EDIT"
                        type="button"
                        class="btn btn--sm"
                        (click)="toggleActive(hsn)"
                      >
                        {{ hsn.active ? 'Deactivate' : 'Activate' }}
                      </button>
                      <button
                        *appHasPermission="permissions.HSN_DELETE"
                        type="button"
                        class="btn btn--sm btn--danger"
                        (click)="remove(hsn, hsn.hsnCode)"
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
      <app-hsn-form
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

      code {
        font-family: var(--font-mono);
        font-size: var(--text-sm);
      }
    `,
  ],
})
export class HsnListComponent extends MasterListPage<HsnCode, HsnCodeRequest> {
  protected readonly service = inject(HsnService);
  protected readonly entityName = 'HSN code';
  protected readonly permissions = Permissions;

  constructor() {
    super();
    // The HSN list sorts by code, not by the base class default of "name".
    this.sortField.set('hsnCode');
    this.reload(0);
  }
}
