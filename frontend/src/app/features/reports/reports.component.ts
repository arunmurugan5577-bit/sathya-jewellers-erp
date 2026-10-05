import { DatePipe } from '@angular/common';
import { HttpContext, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';
import { Permissions } from '../../core/auth/permissions';
import { SUPPRESS_ERROR_TOAST } from '../../core/interceptors/error.interceptor';
import { NotificationService } from '../../core/services/notification.service';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { isApiError } from '../../shared/models/api-error.model';
import { Lookup } from '../../shared/models/lookup.model';
import { ReportColumn, ReportPreview } from '../../shared/models/sales.model';
import { InrPipe } from '../../shared/pipes/inr.pipe';
import { CategoryService } from '../masters/category/category.service';
import { ItemTypeService } from '../masters/item-type/item-type.service';
import { ReportApiService, ReportKind } from '../sales/sales-api.service';

function isoDate(date: Date): string {
  return date.toLocaleDateString('en-CA');
}

/**
 * Stock and sales reports. The server filters, totals and builds the Excel file;
 * this page only collects the filters, shows the first rows and downloads the file.
 */
@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [PageHeaderComponent, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-header title="Reports" subtitle="Preview on screen, then download as Excel." />

      <div class="tabs" role="tablist">
        @if (canView('sales')) {
          <button type="button" role="tab" class="tab" [class.tab--active]="kind() === 'sales'" (click)="switchTo('sales')">Sales report</button>
        }
        @if (canView('stock')) {
          <button type="button" role="tab" class="tab" [class.tab--active]="kind() === 'stock'" (click)="switchTo('stock')">Stock report</button>
        }
        @if (canView('wholesale')) {
          <button type="button" role="tab" class="tab" [class.tab--active]="kind() === 'wholesale'" (click)="switchTo('wholesale')">Wholesale report</button>
        }
      </div>

      <section class="card">
        <div class="toolbar">
          <div class="toolbar__field">
            <label class="toolbar__label" for="start">Start date *</label>
            <input id="start" class="input" type="date" [value]="startDate()" (change)="startDate.set($any($event.target).value)" />
          </div>
          <div class="toolbar__field">
            <label class="toolbar__label" for="end">End date *</label>
            <input id="end" class="input" type="date" [value]="endDate()" (change)="endDate.set($any($event.target).value)" />
          </div>

          @if (kind() === 'wholesale') {
            <div class="toolbar__field">
              <label class="toolbar__label" for="wsStatus">Estimates</label>
              <select id="wsStatus" class="select" (change)="wholesaleStatus.set($any($event.target).value)">
                <option value="COMPLETED">Completed only</option>
                <option value="CANCELLED">Cancelled only</option>
                <option value="ALL">All</option>
              </select>
            </div>
          }

          @if (kind() === 'sales') {
            <div class="toolbar__field">
              <label class="toolbar__label" for="status">Invoices</label>
              <select id="status" class="select" (change)="salesStatus.set($any($event.target).value)">
                <option value="COMPLETED">Completed only</option>
                <option value="CANCELLED">Cancelled only</option>
                <option value="ALL">All</option>
              </select>
            </div>
            <div class="toolbar__field">
              <label class="toolbar__label" for="payment">Payment</label>
              <select id="payment" class="select" (change)="paymentStatus.set($any($event.target).value)">
                <option value="">All</option>
                <option value="PAID">Paid</option>
                <option value="PARTIAL">Part paid</option>
                <option value="UNPAID">Unpaid</option>
              </select>
            </div>
          } @else {
            <div class="toolbar__field">
              <label class="toolbar__label" for="itemType">Item type</label>
              <select id="itemType" class="select" (change)="itemTypeId.set($any($event.target).value)">
                <option value="">All</option>
                @for (type of itemTypes(); track type.id) { <option [value]="type.id">{{ type.name }}</option> }
              </select>
            </div>
            <div class="toolbar__field">
              <label class="toolbar__label" for="category">Category</label>
              <select id="category" class="select" (change)="categoryId.set($any($event.target).value)">
                <option value="">All</option>
                @for (category of categories(); track category.id) { <option [value]="category.id">{{ category.name }}</option> }
              </select>
            </div>
            <div class="toolbar__field">
              <label class="toolbar__label" for="stockStatus">Status</label>
              <select id="stockStatus" class="select" (change)="stockStatus.set($any($event.target).value)">
                <option value="">All</option>
                <option value="AVAILABLE">In stock</option>
                <option value="SOLD">Sold</option>
              </select>
            </div>
          }

          <div class="toolbar__spacer"></div>
          <button type="button" class="btn" [disabled]="loading()" (click)="preview()">{{ loading() ? 'Loading...' : 'Preview' }}</button>
          @if (canExport()) {
            <button type="button" class="btn btn--primary" [disabled]="downloading()" (click)="download()">
              {{ downloading() ? 'Preparing...' : 'Download Excel' }}
            </button>
          }
        </div>
        @if (kind() === 'stock') {
          <p class="hint text-muted">Pieces stocked in between the dates. Weights are net weights; gross weight is set at sale time.</p>
        }
        @if (error()) {
          <div class="alert alert--error hint">{{ error() }}</div>
        }
      </section>

      @if (result(); as report) {
        <p class="text-secondary">
          {{ report.title }} &middot; {{ report.startDate | date: 'dd MMM yyyy' }} to {{ report.endDate | date: 'dd MMM yyyy' }}
          @for (filter of report.filters; track filter) { &middot; {{ filter }} }
        </p>
        @for (sheet of report.sheets; track sheet.name) {
          <section class="card">
            <div class="card__header">
              <h2 class="card__title">{{ sheet.name }}</h2>
              <span class="text-muted">
                {{ sheet.rowCount }} row{{ sheet.rowCount === 1 ? '' : 's' }}{{ sheet.truncated ? ' - showing the first ' + sheet.rows.length + '; download for all' : '' }}
              </span>
            </div>
            <div class="table-wrapper">
              <table class="table">
                <thead>
                  <tr>
                    @for (column of sheet.columns; track column.key) {
                      <th [class.td--numeric]="isNumeric(column)">{{ column.header }}</th>
                    }
                  </tr>
                </thead>
                <tbody>
                  @for (row of sheet.rows; track $index) {
                    <tr>
                      @for (column of sheet.columns; track column.key) {
                        <td [class.td--numeric]="isNumeric(column)">{{ format(column, row[column.key]) }}</td>
                      }
                    </tr>
                  } @empty {
                    <tr><td [attr.colspan]="sheet.columns.length" class="text-muted">No rows for this period.</td></tr>
                  }
                </tbody>
                @if (sheet.rowCount > 0) {
                  <tfoot>
                    <tr class="totals">
                      @for (column of sheet.columns; track column.key; let first = $first) {
                        <td [class.td--numeric]="isNumeric(column)">
                          {{ first ? 'TOTAL' : column.total ? format(column, sheet.totals[column.key]) : '' }}
                        </td>
                      }
                    </tr>
                  </tfoot>
                }
              </table>
            </div>
          </section>
        }
      }
    </div>
  `,
  styles: [
    `
      .tabs { display: flex; gap: var(--space-2); border-bottom: 1px solid var(--border-subtle); }
      .tab { padding: var(--space-2) var(--space-4); border: 0; background: none; color: var(--text-secondary); font: inherit; cursor: pointer; border-bottom: 2px solid transparent; }
      .tab--active { color: var(--brand); border-bottom-color: var(--gold); font-weight: 600; }
      .hint { margin: 0 var(--space-5) var(--space-4); }
      .totals td { font-weight: 700; background: var(--surface-sunken); }
      td { white-space: nowrap; }
    `,
  ],
})
export class ReportsComponent {
  private readonly api = inject(ReportApiService);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly inr = new InrPipe();

  protected readonly kind = signal<ReportKind>(this.firstVisibleKind());
  protected readonly startDate = signal(isoDate(new Date(new Date().getFullYear(), new Date().getMonth(), 1)));
  protected readonly endDate = signal(isoDate(new Date()));
  protected readonly salesStatus = signal('COMPLETED');
  protected readonly wholesaleStatus = signal('COMPLETED');
  protected readonly paymentStatus = signal('');
  protected readonly itemTypeId = signal('');
  protected readonly categoryId = signal('');
  protected readonly stockStatus = signal('');
  protected readonly itemTypes = signal<Lookup[]>([]);
  protected readonly categories = signal<Lookup[]>([]);

  protected readonly result = signal<ReportPreview | null>(null);
  protected readonly loading = signal(false);
  protected readonly downloading = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Which authority each report needs, so adding one is a single entry. */
  private static readonly RIGHTS: Record<ReportKind, { view: string; export: string }> = {
    sales: { view: Permissions.REPORT_SALES_VIEW, export: Permissions.REPORT_SALES_EXPORT },
    stock: { view: Permissions.REPORT_STOCK_VIEW, export: Permissions.REPORT_STOCK_EXPORT },
    wholesale: { view: Permissions.REPORT_WHOLESALE_VIEW, export: Permissions.REPORT_WHOLESALE_EXPORT },
  };

  protected readonly canExport = computed(() =>
    this.auth.has(ReportsComponent.RIGHTS[this.kind()].export),
  );

  constructor() {
    inject(ItemTypeService).lookup().subscribe((values) => this.itemTypes.set(values));
    inject(CategoryService).lookup().subscribe((values) => this.categories.set(values));
  }

  protected canView(kind: ReportKind): boolean {
    return this.auth.has(ReportsComponent.RIGHTS[kind].view);
  }

  /** Opens on the first report this user may actually see. */
  private firstVisibleKind(): ReportKind {
    const order: ReportKind[] = ['sales', 'stock', 'wholesale'];
    return order.find((kind) => this.auth.has(ReportsComponent.RIGHTS[kind].view)) ?? 'sales';
  }

  protected switchTo(kind: ReportKind): void {
    this.kind.set(kind);
    this.result.set(null);
    this.error.set(null);
  }

  protected isNumeric(column: ReportColumn): boolean {
    return ['INTEGER', 'WEIGHT', 'MONEY', 'PERCENT'].includes(column.type);
  }

  protected format(column: ReportColumn, value: string | number | null | undefined): string {
    if (value === null || value === undefined || value === '') {
      return '';
    }
    switch (column.type) {
      case 'MONEY':
        return this.inr.transform(value);
      case 'WEIGHT':
        return Number(value).toFixed(3);
      case 'DATE':
        return String(value).split('-').reverse().join('-');
      case 'DATETIME':
        return String(value).replace('T', ' ').slice(0, 16);
      default:
        return String(value);
    }
  }

  protected preview(): void {
    if (!this.validate()) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.api.preview(this.kind(), this.params(), new HttpContext().set(SUPPRESS_ERROR_TOAST, true)).subscribe({
      next: (report) => {
        this.loading.set(false);
        this.result.set(report);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.error.set(error instanceof HttpErrorResponse && isApiError(error.error) ? error.error.message : 'Could not load the report.');
      },
    });
  }

  protected download(): void {
    if (!this.validate()) {
      return;
    }
    this.downloading.set(true);
    this.error.set(null);
    this.api.export(this.kind(), this.params()).subscribe({
      next: (response) => {
        this.downloading.set(false);
        const disposition = response.headers.get('Content-Disposition') ?? '';
        const name = /filename="?([^";]+)"?/.exec(disposition)?.[1] ?? `${this.kind()}-report.xlsx`;
        const url = URL.createObjectURL(response.body!);
        const link = document.createElement('a');
        link.href = url;
        link.download = name;
        link.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
        this.notifications.success(`${name} downloaded.`);
      },
      error: async (error: unknown) => {
        this.downloading.set(false);
        // The error body of a blob request is itself a blob; read the JSON out of it.
        let message = 'Could not generate the Excel file.';
        if (error instanceof HttpErrorResponse && error.error instanceof Blob) {
          try {
            const body = JSON.parse(await error.error.text());
            message = isApiError(body) ? body.message : message;
          } catch {
            /* keep the generic message */
          }
        }
        this.error.set(message);
      },
    });
  }

  private validate(): boolean {
    if (!this.startDate() || !this.endDate()) {
      this.error.set('Start date and end date are required.');
      return false;
    }
    if (this.startDate() > this.endDate()) {
      this.error.set('End date cannot be before the start date.');
      return false;
    }
    return true;
  }

  private params(): Record<string, string> {
    const base = { startDate: this.startDate(), endDate: this.endDate() };
    switch (this.kind()) {
      case 'sales':
        return { ...base, status: this.salesStatus(), paymentStatus: this.paymentStatus() };
      case 'wholesale':
        return { ...base, status: this.wholesaleStatus() };
      default:
        return { ...base, itemTypeId: this.itemTypeId(), categoryId: this.categoryId(), status: this.stockStatus() };
    }
  }
}
