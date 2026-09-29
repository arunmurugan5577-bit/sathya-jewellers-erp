import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';

import { Permissions } from '../../core/auth/permissions';
import { DataTableComponent } from '../../shared/components/data-table.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { PaginatorComponent } from '../../shared/components/paginator.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { DEFAULT_PAGE_SIZE, Page, emptyPage } from '../../shared/models/page.model';
import { SaleSummary } from '../../shared/models/sales.model';
import { InrPipe } from '../../shared/pipes/inr.pipe';
import { DocumentFilters, SaleApiService } from './sales-api.service';
import { PaymentStatusBadgeComponent } from './status-badges.component';

@Component({
  selector: 'app-sale-list',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    PageHeaderComponent,
    DataTableComponent,
    PaginatorComponent,
    HasPermissionDirective,
    InrPipe,
    PaymentStatusBadgeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-header title="Sales" subtitle="Tax invoices, newest first.">
        <a *appHasPermission="permissions.SALES_CREATE" class="btn btn--primary" routerLink="/sales/new">New sale</a>
      </app-page-header>

      <section class="card">
        <div class="toolbar">
          <div class="toolbar__field">
            <label class="toolbar__label" for="search">Search</label>
            <input id="search" class="input" type="search" placeholder="Invoice no, name, mobile" (input)="onSearch($event)" />
          </div>
          <div class="toolbar__field">
            <label class="toolbar__label" for="from">From</label>
            <input id="from" class="input" type="date" (change)="setFilter('from', $event)" />
          </div>
          <div class="toolbar__field">
            <label class="toolbar__label" for="to">To</label>
            <input id="to" class="input" type="date" (change)="setFilter('to', $event)" />
          </div>
          <div class="toolbar__field">
            <label class="toolbar__label" for="status">Status</label>
            <select id="status" class="select" (change)="setFilter('status', $event)">
              <option value="">All</option>
              <option value="COMPLETED">Completed</option>
              <option value="CANCELLED">Cancelled</option>
            </select>
          </div>
          <div class="toolbar__field">
            <label class="toolbar__label" for="payment">Payment</label>
            <select id="payment" class="select" (change)="setFilter('paymentStatus', $event)">
              <option value="">All</option>
              <option value="PAID">Paid</option>
              <option value="PARTIAL">Part paid</option>
              <option value="UNPAID">Unpaid</option>
            </select>
          </div>
        </div>

        <app-data-table [loading]="loading()" [isEmpty]="page().content.length === 0" emptyTitle="No invoices found">
          <table class="table">
            <thead>
              <tr>
                <th>Invoice</th>
                <th>Date</th>
                <th>Customer</th>
                <th class="td--numeric">Grand total</th>
                <th class="td--numeric">Net payable</th>
                <th class="td--numeric">Balance</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              @for (sale of page().content; track sale.id) {
                <tr [class.row--cancelled]="sale.status === 'CANCELLED'">
                  <td><a class="table__serial" [routerLink]="['/sales', sale.id]">{{ sale.invoiceNumber }}</a></td>
                  <td>{{ sale.invoiceDate | date: 'dd-MM-yyyy' }}</td>
                  <td>{{ sale.customerName }}<div class="text-muted">{{ sale.customerMobile }}</div></td>
                  <td class="td--numeric">{{ sale.grandTotal | inr }}</td>
                  <td class="td--numeric">{{ sale.netPayable | inr }}</td>
                  <td class="td--numeric">{{ sale.balanceAmount | inr }}</td>
                  <td><app-payment-status-badge [status]="sale.status === 'CANCELLED' ? 'CANCELLED' : sale.paymentStatus" /></td>
                </tr>
              }
            </tbody>
          </table>
        </app-data-table>

        <app-paginator [page]="page()" (pageChange)="reload($event)" (sizeChange)="onSizeChange($event)" />
      </section>
    </div>
  `,
  styles: [`.row--cancelled td { color: var(--text-muted); text-decoration: line-through; } .row--cancelled td:last-child { text-decoration: none; }`],
})
export class SaleListComponent {
  private readonly api = inject(SaleApiService);
  private readonly search$ = new Subject<string>();

  protected readonly permissions = Permissions;
  protected readonly loading = signal(true);
  protected readonly page = signal<Page<SaleSummary>>(emptyPage<SaleSummary>());
  private filters: DocumentFilters = {};

  constructor() {
    this.search$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe((search) => {
        this.filters = { ...this.filters, search };
        this.reload(0);
      });
    this.reload(0);
  }

  protected reload(page = this.page().page): void {
    this.loading.set(true);
    this.api
      .list({ page, size: this.page().size || DEFAULT_PAGE_SIZE, sort: 'id', direction: 'desc' }, this.filters)
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

  protected setFilter(key: keyof DocumentFilters, event: Event): void {
    this.filters = { ...this.filters, [key]: (event.target as HTMLInputElement).value || null };
    this.reload(0);
  }

  protected onSizeChange(size: number): void {
    this.page.update((current) => ({ ...current, size }));
    this.reload(0);
  }
}
