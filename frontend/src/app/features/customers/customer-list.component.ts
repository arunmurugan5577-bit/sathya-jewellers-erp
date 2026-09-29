import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';

import { Permissions } from '../../core/auth/permissions';
import { ConfirmService } from '../../core/services/confirm.service';
import { NotificationService } from '../../core/services/notification.service';
import { DataTableComponent } from '../../shared/components/data-table.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { PaginatorComponent } from '../../shared/components/paginator.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { DEFAULT_PAGE_SIZE, Page, emptyPage } from '../../shared/models/page.model';
import { Customer } from '../../shared/models/sales.model';
import { CustomerApiService } from '../sales/sales-api.service';
import { CustomerFormComponent } from './customer-form.component';

@Component({
  selector: 'app-customer-list',
  standalone: true,
  imports: [
    PageHeaderComponent,
    DataTableComponent,
    PaginatorComponent,
    StatusBadgeComponent,
    HasPermissionDirective,
    CustomerFormComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-header title="Customers" subtitle="Buyers and sellers of old gold and silver.">
        <button *appHasPermission="permissions.CUSTOMER_CREATE" type="button" class="btn btn--primary" (click)="openForm(null)">
          New customer
        </button>
      </app-page-header>

      <section class="card">
        <div class="toolbar">
          <div class="toolbar__field">
            <label class="toolbar__label" for="search">Search</label>
            <input id="search" class="input" type="search" placeholder="Name, mobile or code" (input)="onSearch($event)" />
          </div>
        </div>

        <app-data-table [loading]="loading()" [isEmpty]="page().content.length === 0" emptyTitle="No customers found">
          <table class="table">
            <thead>
              <tr>
                <th>Code</th>
                <th>Name</th>
                <th>Mobile</th>
                <th>City</th>
                <th>GSTIN</th>
                <th>Status</th>
                <th class="td--actions">Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (customer of page().content; track customer.id) {
                <tr>
                  <td class="table__serial">{{ customer.customerCode }}</td>
                  <td>{{ customer.fullName }}</td>
                  <td>{{ customer.mobileNumber || '-' }}</td>
                  <td>{{ customer.city || '-' }}</td>
                  <td>{{ customer.gstin || '-' }}</td>
                  <td><app-status-badge [active]="customer.active" /></td>
                  <td class="td--actions">
                    <div class="row row--end">
                      <button *appHasPermission="permissions.CUSTOMER_EDIT" type="button" class="btn btn--sm" (click)="openForm(customer)">Edit</button>
                      <button *appHasPermission="permissions.CUSTOMER_EDIT" type="button" class="btn btn--sm" (click)="toggleActive(customer)">
                        {{ customer.active ? 'Deactivate' : 'Activate' }}
                      </button>
                      <button *appHasPermission="permissions.CUSTOMER_DELETE" type="button" class="btn btn--sm btn--danger" (click)="remove(customer)">Delete</button>
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </app-data-table>

        <app-paginator [page]="page()" (pageChange)="reload($event)" (sizeChange)="onSizeChange($event)" />
      </section>
    </div>

    @if (formOpen()) {
      <app-customer-form [customer]="editing()" (saved)="onSaved()" (closed)="formOpen.set(false)" />
    }
  `,
})
export class CustomerListComponent {
  private readonly api = inject(CustomerApiService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmService);
  private readonly search$ = new Subject<string>();

  protected readonly permissions = Permissions;
  protected readonly loading = signal(true);
  protected readonly page = signal<Page<Customer>>(emptyPage<Customer>());
  protected readonly search = signal('');
  protected readonly formOpen = signal(false);
  protected readonly editing = signal<Customer | null>(null);

  constructor() {
    this.search$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe((term) => {
        this.search.set(term);
        this.reload(0);
      });
    this.reload(0);
  }

  protected reload(page = this.page().page): void {
    this.loading.set(true);
    this.api
      .list({ page, size: this.page().size || DEFAULT_PAGE_SIZE, sort: 'id', direction: 'desc' }, this.search())
      .subscribe({
        next: (result) => {
          this.page.set(result);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  protected onSearch(event: Event): void {
    this.search$.next((event.target as HTMLInputElement).value.trim());
  }

  protected onSizeChange(size: number): void {
    this.page.update((current) => ({ ...current, size }));
    this.reload(0);
  }

  protected openForm(customer: Customer | null): void {
    this.editing.set(customer);
    this.formOpen.set(true);
  }

  protected onSaved(): void {
    this.formOpen.set(false);
    this.reload();
  }

  protected toggleActive(customer: Customer): void {
    this.api.setActive(customer.id, !customer.active).subscribe({
      next: () => {
        this.notifications.success(`${customer.fullName} ${customer.active ? 'deactivated' : 'activated'}.`);
        this.reload();
      },
    });
  }

  protected async remove(customer: Customer): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: 'Delete customer',
      message: `Delete ${customer.fullName}? Customers with sales or purchase bills cannot be deleted - deactivate them instead.`,
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!confirmed) {
      return;
    }
    this.api.delete(customer.id).subscribe({
      next: () => {
        this.notifications.success(`${customer.fullName} deleted.`);
        this.reload();
      },
    });
  }
}
