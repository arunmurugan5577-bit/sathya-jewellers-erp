import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { isApiError } from '../../shared/models/api-error.model';
import { Sale } from '../../shared/models/sales.model';
import { SpinnerComponent } from '../../shared/components/spinner.component';
import { InvoicePrintComponent } from './invoice-print.component';
import { SaleApiService } from './sales-api.service';

/**
 * Every invoice of a date range, one after another, for printing in one go.
 *
 * <p>The same {@link InvoicePrintComponent} the single invoice screen uses,
 * repeated - so a run of bills is laid out exactly as each one prints on its
 * own, and there is no second copy of the invoice design to keep in step. Each
 * sheet starts a new page.
 *
 * <p>There is no PDF written here. The browser's own print dialog has "Save as
 * PDF", which produces one file of the whole run; writing a PDF on the server
 * would mean redrawing the invoice in a second place and the two would drift.
 */
@Component({
  selector: 'app-invoice-batch-print',
  standalone: true,
  imports: [RouterLink, DatePipe, SpinnerComponent, InvoicePrintComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="no-print bar">
      <a class="btn btn--ghost" routerLink="/sales">Back to invoices</a>
      @if (sales().length) {
        <span class="bar__count">
          {{ sales().length }} invoice{{ sales().length === 1 ? '' : 's' }},
          {{ from() | date: 'dd MMM yyyy' }} to {{ to() | date: 'dd MMM yyyy' }}
        </span>
        <button type="button" class="btn btn--primary" (click)="print()">Print / Save as PDF</button>
      }
    </div>

    @if (loading()) {
      <app-spinner label="Gathering the invoices..." />
    } @else if (error()) {
      <div class="no-print alert alert--error">{{ error() }}</div>
    } @else if (!sales().length) {
      <div class="no-print alert alert--info">
        No invoices in that range. Cancelled invoices are not included.
      </div>
    } @else {
      @for (sale of sales(); track sale.id) {
        <div class="sheet">
          <app-invoice-print [sale]="sale" />
        </div>
      }
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }

      .bar {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--space-3);
        margin-bottom: var(--space-4);
      }

      .bar__count {
        color: var(--text-secondary);
      }

      /*
       * One invoice, one sheet. break-after rather than a margin, so the run
       * paginates on the page boundary instead of wherever the content happens
       * to reach - and the last one does not leave a blank sheet behind it.
       */
      .sheet {
        break-after: page;
        page-break-after: always;
      }

      .sheet:last-child {
        break-after: auto;
        page-break-after: auto;
      }

      @media screen {
        .sheet + .sheet {
          margin-top: var(--space-6);
        }
      }
    `,
  ],
})
export class InvoiceBatchPrintComponent {
  private readonly api = inject(SaleApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly sales = signal<Sale[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly from = signal('');
  protected readonly to = signal('');

  constructor() {
    const params = this.route.snapshot.queryParamMap;
    const start = params.get('startDate');
    const end = params.get('endDate');
    if (!start || !end) {
      void this.router.navigate(['/sales']);
      return;
    }
    this.from.set(start);
    this.to.set(end);

    inject(DestroyRef);
    this.api.forPrinting(start, end).subscribe({
      next: (found) => {
        this.sales.set(found);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(
          err instanceof HttpErrorResponse && isApiError(err.error)
            ? err.error.message
            : 'Could not gather the invoices. Try again.',
        );
      },
    });
  }

  protected print(): void {
    window.print();
  }
}
