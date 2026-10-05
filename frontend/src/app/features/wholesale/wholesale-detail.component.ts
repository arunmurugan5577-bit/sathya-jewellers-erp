import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { NotificationService } from '../../core/services/notification.service';
import { Permissions } from '../../core/auth/permissions';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { WholesaleEstimate } from '../../shared/models/wholesale.model';
import { InrPipe } from '../../shared/pipes/inr.pipe';
import { WeightPipe } from '../../shared/pipes/weight.pipe';
import { WholesaleApiService } from './wholesale-api.service';

/**
 * One estimate, laid out as the shop's own slip.
 *
 * <p>Everything shown is read back from the saved document, not recomputed:
 * the opening balance and the rate were true on the day, and reprinting must
 * show what was handed over.
 */
@Component({
  selector: 'app-wholesale-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, PageHeaderComponent, HasPermissionDirective, InrPipe, WeightPipe],
  template: `
    @if (estimate(); as e) {
      <div class="page">
        <app-page-header [title]="'Estimate ' + e.estimateNumber" [subtitle]="e.customerName + ' · ' + e.estimateDate">
          <a class="btn btn--ghost" routerLink="/wholesale">All estimates</a>
          <button type="button" class="btn btn--primary" (click)="print()">Print</button>
        </app-page-header>

        @if (e.status === 'CANCELLED') {
          <p class="banner">Cancelled{{ e.cancelReason ? ' — ' + e.cancelReason : '' }}</p>
        }

        <div class="slip" id="wholesale-slip">
          <header class="slip__head">
            <div>
              <strong>{{ e.sellerName }}</strong>
              <div class="muted">{{ e.sellerAddress }}</div>
            </div>
            <div class="right">
              <div>Est No. <strong>{{ e.estimateNumber }}</strong></div>
              <div>Date: {{ e.estimateDate }}</div>
            </div>
          </header>

          <div class="slip__party">
            <div>To: <strong>{{ e.customerName }}</strong></div>
            <div>G Pure Rate: <strong>{{ e.pureRatePerGram | inr: 'whole' }}/Gm</strong></div>
          </div>

          <div class="slip__balance">
            OpBal Misc Amt: {{ e.totals.openingMiscAmount | inr: 'whole' }}
            &amp; GPure: {{ e.totals.openingPureGrams | weight }} Gm
            <span class="right">Op Bal Value: {{ e.totals.openingValue | inr: 'whole' }}</span>
          </div>

          <table class="table slip__table">
            <thead>
              <tr>
                <th>#</th>
                <th>Serial</th>
                <th>Jewel Name</th>
                <th class="num">Jewell Wt</th>
                <th class="num">Pure %</th>
                <th class="num">Pure Wt</th>
                <th class="num">Rate</th>
                <th class="num">MC</th>
                <th class="num">Stone</th>
                <th class="num">Item Amount</th>
              </tr>
            </thead>
            <tbody>
              @for (line of e.items; track line.lineNumber) {
                <tr>
                  <td>{{ line.lineNumber }}</td>
                  <td>{{ line.serialNumber }}</td>
                  <td>{{ line.jewelName }}</td>
                  <td class="num">{{ line.jewelWeightGrams | weight }}</td>
                  <td class="num">{{ line.purePercentage }}</td>
                  <td class="num">{{ line.pureWeightGrams | weight }}</td>
                  <td class="num">{{ line.ratePerGram | inr: 'whole' }}</td>
                  <td class="num">{{ line.makingCharge | inr: 'whole' }}</td>
                  <td class="num">{{ line.stoneAmount | inr: 'whole' }}</td>
                  <td class="num">{{ line.itemAmount | inr: 'whole' }}</td>
                </tr>
              }
            </tbody>
            <tfoot>
              <tr>
                <td colspan="5" class="right">Tot G Pure</td>
                <td class="num"><strong>{{ e.totals.totalPureGrams | weight }}</strong></td>
                <td colspan="3" class="right">Gold Total</td>
                <td class="num"><strong>{{ e.totals.totalAmount | inr: 'whole' }}</strong></td>
              </tr>
            </tfoot>
          </table>

          <div class="slip__balance">
            ClBal Misc Amt: {{ e.totals.closingMiscAmount | inr: 'whole' }}
            &amp; GPure: {{ e.totals.closingPureGrams | weight }} Gm
            <span class="right">Cl Bal Value: <strong>{{ e.totals.closingValue | inr: 'whole' }}</strong></span>
          </div>

          <p class="words">Rupees: {{ e.totals.totalAmountInWords }}</p>
          @if (e.remarks) {
            <p class="muted">{{ e.remarks }}</p>
          }
        </div>

        @if (e.status === 'COMPLETED') {
          <section class="card no-print">
            <div class="card__body">
              <button type="button" class="btn btn--danger" *appHasPermission="permissions.WHOLESALE_DELETE"
                      [disabled]="cancelling()" (click)="cancel()">
                {{ cancelling() ? 'Cancelling...' : 'Cancel estimate' }}
              </button>
              <p class="field__hint">
                Cancelling returns the pieces to stock and reverses this estimate from the party's account.
              </p>
            </div>
          </section>
        }
      </div>
    } @else {
      <div class="page"><p class="text-muted">Loading...</p></div>
    }
  `,
  styles: [
    `
      .slip { background: #fff; color: #000; padding: var(--space-5); border-radius: var(--radius-md); }
      .slip__head { display: flex; justify-content: space-between; gap: var(--space-4); border-bottom: 2px solid #000; padding-bottom: var(--space-3); }
      .slip__party, .slip__balance { display: flex; justify-content: space-between; gap: var(--space-4); padding: var(--space-2) 0; font-size: var(--text-sm); }
      .slip__balance { border-top: 1px solid #999; border-bottom: 1px solid #999; }
      .slip__table { width: 100%; margin: var(--space-3) 0; }
      .slip__table th, .slip__table td { padding: 4px 6px; border-bottom: 1px solid #ddd; }
      .num { text-align: right; font-variant-numeric: tabular-nums; }
      .right { text-align: right; }
      .muted { color: #555; font-size: var(--text-sm); }
      .words { font-style: italic; font-size: var(--text-sm); }
      .banner { background: color-mix(in srgb, var(--danger) 15%, transparent); color: var(--danger); padding: var(--space-3) var(--space-4); border-radius: var(--radius-sm); font-weight: 600; }
      @media print {
        .no-print, app-page-header, .banner { display: none !important; }
        .slip { padding: 0; }
      }
    `,
  ],
})
export class WholesaleDetailComponent {
  private readonly api = inject(WholesaleApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  protected readonly permissions = Permissions;
  protected readonly estimate = signal<WholesaleEstimate | null>(null);
  protected readonly cancelling = signal(false);

  constructor() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.findById(id).subscribe((estimate) => {
      this.estimate.set(estimate);
      // Saving navigates here with ?print=1 so the slip comes up ready.
      if (this.route.snapshot.queryParamMap.get('print') === '1') {
        setTimeout(() => window.print(), 300);
      }
    });
  }

  protected print(): void {
    window.print();
  }

  protected cancel(): void {
    const current = this.estimate();
    if (!current || this.cancelling()) {
      return;
    }
    const reason = window.prompt('Why is this estimate being cancelled?') ?? '';
    if (!reason.trim()) {
      return;
    }
    this.cancelling.set(true);
    this.api.cancel(current.id, reason.trim()).subscribe({
      next: (updated) => {
        this.cancelling.set(false);
        this.estimate.set(updated);
        this.notifications.success(`Estimate ${updated.estimateNumber} cancelled.`);
      },
      error: () => this.cancelling.set(false),
    });
  }
}
