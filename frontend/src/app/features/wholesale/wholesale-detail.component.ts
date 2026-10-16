import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { NotificationService } from '../../core/services/notification.service';
import { Permissions } from '../../core/auth/permissions';
import { isApiError } from '../../shared/models/api-error.model';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ModalComponent } from '../../shared/components/modal.component';
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
  imports: [RouterLink, PageHeaderComponent, ModalComponent, HasPermissionDirective, InrPipe, WeightPipe],
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
                <th>Item Name</th>
                <th class="num">Item Weight</th>
                <th class="num">Pure %</th>
                <th class="num">Pure Wt</th>
                <th class="num">Rate</th>
                <th class="num">MC</th>
                <th class="num">Stone</th>
                <th class="num">Amount</th>
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

        <section class="card no-print">
          <div class="card__body">
            @if (e.status === 'COMPLETED') {
              <button type="button" class="btn btn--danger" *appHasPermission="permissions.WHOLESALE_DELETE"
                      [disabled]="cancelling()" (click)="cancel()">
                {{ cancelling() ? 'Cancelling...' : 'Cancel estimate' }}
              </button>
              <p class="field__hint">
                Cancelling returns the pieces to stock and reverses this estimate from the party's account.
              </p>
            }
            <!-- Offered on a cancelled estimate too: clearing out the ones the
                 shop need not keep is the other half of cancelling. -->
            <button type="button" class="btn btn--danger" *appHasPermission="permissions.WHOLESALE_DELETE"
                    [disabled]="busy()" (click)="deleteOpen.set(true)">
              Delete estimate
            </button>
            <p class="field__hint">
              Deleting erases it altogether. {{ e.status === 'CANCELLED'
                ? 'The pieces and the account went back when it was cancelled.'
                : 'The pieces and the account go back first, exactly as cancelling does.' }}
            </p>
          </div>
        </section>

        @if (deleteOpen()) {
          <app-modal [title]="'Delete ' + e.estimateNumber" (closed)="deleteOpen.set(false)">
            <p>
              <strong>This erases the estimate.</strong> It will not appear in the books or in a
              report - unlike cancelling, which keeps it on record.
            </p>
            <p class="text-muted">
              {{ e.estimateNumber }} is freed for the next estimate if it was the last number issued.
              An older number stays spent.
            </p>
            <div class="field">
              <label class="field__label field__label--required" for="confirmNumber">
                Type {{ e.estimateNumber }} to confirm
              </label>
              <input id="confirmNumber" class="input" autocomplete="off"
                     (input)="deleteConfirm.set($any($event.target).value)" />
            </div>
            @if (deleteError()) { <div class="alert alert--error">{{ deleteError() }}</div> }
            <ng-container modalActions>
              <button type="button" class="btn btn--ghost" (click)="deleteOpen.set(false)">Keep estimate</button>
              <button type="button" class="btn btn--danger"
                      [disabled]="busy() || deleteConfirm().trim() !== e.estimateNumber"
                      (click)="remove(e)">Delete for good</button>
            </ng-container>
          </app-modal>
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
  protected readonly deleteOpen = signal(false);
  /** Typed back by the user, so a stray click cannot erase an estimate. */
  protected readonly deleteConfirm = signal('');
  protected readonly busy = signal(false);
  protected readonly deleteError = signal<string | null>(null);

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

  protected remove(estimate: WholesaleEstimate): void {
    this.busy.set(true);
    this.deleteError.set(null);
    this.api.delete(estimate.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.deleteOpen.set(false);
        this.notifications.success(`Estimate ${estimate.estimateNumber} deleted.`);
        void this.router.navigate(['/wholesale']);
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.deleteError.set(
          err instanceof HttpErrorResponse && isApiError(err.error)
            ? err.error.message
            : 'Could not delete the estimate. Try again.',
        );
      },
    });
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
