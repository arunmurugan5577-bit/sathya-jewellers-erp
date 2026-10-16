import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { isApiError } from '../../shared/models/api-error.model';
import { OldMetalTransaction } from '../../shared/models/sales.model';
import { SpinnerComponent } from '../../shared/components/spinner.component';
import { OldMetalApiService } from '../sales/sales-api.service';
import { OldMetalBillComponent } from './old-metal-bill.component';

/**
 * Every purchase bill of a date range, one per sheet, for printing in one go.
 *
 * <p>The same {@link OldMetalBillComponent} the single bill screen uses, so a
 * run looks exactly like the bills it is made of and there is no second copy
 * of the design to keep in step. The browser's print dialog saves the run as
 * one PDF; nothing is drawn on the server.
 */
@Component({
  selector: 'app-old-metal-batch-print',
  standalone: true,
  imports: [RouterLink, DatePipe, SpinnerComponent, OldMetalBillComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="no-print bar">
      <a class="btn btn--ghost" routerLink="/old-metal">Back to purchase bills</a>
      @if (bills().length) {
        <span class="bar__count">
          {{ bills().length }} bill{{ bills().length === 1 ? '' : 's' }},
          {{ from() | date: 'dd MMM yyyy' }} to {{ to() | date: 'dd MMM yyyy' }}
        </span>
        <button type="button" class="btn btn--primary" (click)="print()">Print / Save as PDF</button>
      }
    </div>

    @if (loading()) {
      <app-spinner label="Gathering the purchase bills..." />
    } @else if (error()) {
      <div class="no-print alert alert--error">{{ error() }}</div>
    } @else if (!bills().length) {
      <div class="no-print alert alert--info">
        No purchase bills in that range. Cancelled bills are not included.
      </div>
    } @else {
      @for (bill of bills(); track bill.id) {
        <div class="sheet">
          <app-old-metal-bill [bill]="bill" />
        </div>
      }
    }
  `,
  styles: [
    `
      :host { display: block; }
      .bar { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); margin-bottom: var(--space-4); }
      .bar__count { color: var(--text-secondary); }

      /* One bill, one sheet; the last leaves no blank page behind it. */
      .sheet { break-after: page; page-break-after: always; }
      .sheet:last-child { break-after: auto; page-break-after: auto; }

      @media screen {
        .sheet + .sheet { margin-top: var(--space-6); }
      }
    `,
  ],
})
export class OldMetalBatchPrintComponent {
  private readonly api = inject(OldMetalApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly bills = signal<OldMetalTransaction[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly from = signal('');
  protected readonly to = signal('');

  constructor() {
    const params = this.route.snapshot.queryParamMap;
    const start = params.get('startDate');
    const end = params.get('endDate');
    if (!start || !end) {
      void this.router.navigate(['/old-metal']);
      return;
    }
    this.from.set(start);
    this.to.set(end);

    this.api.forPrinting(start, end).subscribe({
      next: (found) => {
        this.bills.set(found);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(
          err instanceof HttpErrorResponse && isApiError(err.error)
            ? err.error.message
            : 'Could not gather the purchase bills. Try again.',
        );
      },
    });
  }

  protected print(): void {
    window.print();
  }
}
