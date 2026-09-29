import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { Permissions } from '../../core/auth/permissions';
import { NotificationService } from '../../core/services/notification.service';
import { ModalComponent } from '../../shared/components/modal.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { SpinnerComponent } from '../../shared/components/spinner.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { isApiError } from '../../shared/models/api-error.model';
import { OldMetalTransaction } from '../../shared/models/sales.model';
import { InrPipe } from '../../shared/pipes/inr.pipe';
import { OldMetalApiService } from '../sales/sales-api.service';
import { PaymentStatusBadgeComponent } from '../sales/status-badges.component';

/** A purchase bill, on screen and as printed ("PURCHASE BILL"), plus its usage and cancellation. */
@Component({
  selector: 'app-old-metal-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, PageHeaderComponent, SpinnerComponent, ModalComponent, HasPermissionDirective, InrPipe, PaymentStatusBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (bill(); as b) {
      <div class="stack">
        <div class="no-print">
          <app-page-header [title]="'Purchase bill ' + b.transactionNumber" [subtitle]="b.customerName">
            <a class="btn btn--ghost" routerLink="/old-metal">All purchase bills</a>
            <button type="button" class="btn btn--primary" (click)="print()">Print</button>
            @if (b.status === 'AVAILABLE') {
              <button *appHasPermission="permissions.OLD_METAL_DELETE" type="button" class="btn btn--danger" (click)="cancelOpen.set(true)">Cancel bill</button>
            }
          </app-page-header>
          <div class="summary">
            <app-payment-status-badge [status]="b.status" />
            <span>Total <strong>{{ b.totalAmount | inr }}</strong></span>
            <span>Used on sales <strong>{{ b.usedAmount | inr }}</strong></span>
            <span>Available <strong>{{ b.availableAmount | inr }}</strong></span>
          </div>
          @if (b.status === 'CANCELLED') {
            <div class="alert alert--warning">Cancelled by {{ b.cancelledBy }} on {{ b.cancelledAt | date: 'dd MMM yyyy, HH:mm' }}: {{ b.cancelReason }}</div>
          }
        </div>

        <article class="bill">
          @if (b.status === 'CANCELLED') { <div class="bill__void">CANCELLED</div> }
          <header class="bill__seller">
            <img class="bill__logo" src="assets/brand/logo-icon.png" alt="" width="384" height="384" />
            <div>
              <h1 class="bill__shop">{{ b.sellerName }}</h1>
              @if (b.sellerAddress) { <p>{{ b.sellerAddress }}</p> }
              <p>
                @if (b.sellerMobile) { <span>Cell: {{ b.sellerMobile }}</span> }
                @if (b.sellerGstin) { <span class="gstin">GSTIN: {{ b.sellerGstin }}</span> }
              </p>
            </div>
          </header>
          <div class="bill__title">PURCHASE BILL</div>
          <section class="bill__meta">
            <div>
              <div><span class="label">From:</span> <strong>{{ b.customerName }}</strong></div>
              @if (b.customerAddress) { <div>{{ b.customerAddress }}</div> }
              @if (b.customerMobile) { <div>Cell: {{ b.customerMobile }}</div> }
            </div>
            <div class="right">
              <div><span class="label">No.</span> <strong>{{ b.transactionNumber }}</strong></div>
              <div><span class="label">Date:</span> {{ b.transactionDate | date: 'dd-MM-yyyy' }}</div>
            </div>
          </section>
          <table class="bill__lines">
            <thead>
              <tr><th>S.No</th><th class="left">Particulars</th><th>HSN</th><th>Net Wt</th><th>Gross Wt</th><th>Rate</th><th>Amount</th></tr>
            </thead>
            <tbody>
              @for (item of b.items; track item.lineNumber) {
                <tr>
                  <td class="center">{{ item.lineNumber }}</td>
                  <td class="left">{{ item.particulars }}<div class="sub">{{ item.itemTypeName }} {{ item.purityName || '' }}</div></td>
                  <td class="center">{{ item.hsnCode }}</td>
                  <td>{{ item.netWeightGrams.toFixed(3) }}</td>
                  <td>{{ item.grossWeightGrams != null ? item.grossWeightGrams.toFixed(3) : '-' }}</td>
                  <td>{{ item.ratePerGram | inr: 'whole' }}</td>
                  <td class="strong">{{ item.amount | inr: 'whole' }}</td>
                </tr>
              }
            </tbody>
            <tfoot>
              <tr class="grand"><td colspan="6" class="right">TOTAL</td><td>{{ b.totalAmount | inr }}</td></tr>
            </tfoot>
          </table>
          <p><span class="label">Rupees:</span> {{ b.amountInWords }}</p>
          <footer class="bill__sign"><div>Seller's signature</div><div>For {{ b.sellerName }}</div></footer>
        </article>
      </div>

      @if (cancelOpen()) {
        <app-modal [title]="'Cancel ' + b.transactionNumber" (closed)="cancelOpen.set(false)">
          <div class="field">
            <label class="field__label field__label--required" for="reason">Reason</label>
            <textarea id="reason" class="textarea" rows="3" maxlength="500" (input)="reason.set($any($event.target).value)"></textarea>
          </div>
          @if (error()) { <div class="alert alert--error">{{ error() }}</div> }
          <ng-container modalActions>
            <button type="button" class="btn btn--ghost" (click)="cancelOpen.set(false)">Keep bill</button>
            <button type="button" class="btn btn--danger" [disabled]="!reason().trim()" (click)="cancel(b)">Cancel bill</button>
          </ng-container>
        </app-modal>
      }
    } @else {
      <app-spinner />
    }
  `,
  styles: [
    `
      .summary { display: flex; flex-wrap: wrap; gap: var(--space-3) var(--space-6); align-items: center; margin: var(--space-3) 0; }
      .bill { position: relative; max-width: 190mm; margin: 0 auto; padding: 8mm; background: #fff; color: #1a1a1a; border: 1px solid #d8cfc0; font-size: 12px; overflow-x: auto; }
      .bill p { margin: 0; }
      .bill__void { position: absolute; top: 40%; left: 50%; transform: translate(-50%, -50%) rotate(-20deg); font-size: 64px; font-weight: 800; color: rgba(180,0,0,.18); }
      .bill__seller { display: flex; gap: 12px; align-items: center; justify-content: center; text-align: center; border-bottom: 2px solid #7a1f2b; padding-bottom: 6px; }
      .bill__logo { width: 64px; height: 64px; border-radius: 50%; flex-shrink: 0; }
      .bill__shop { margin: 0; font-size: 22px; color: #7a1f2b; text-transform: uppercase; letter-spacing: .04em; }
      .gstin { margin-left: 12px; font-weight: 600; }
      .bill__title { text-align: center; margin: 6px auto; font-weight: 700; letter-spacing: .2em; background: #7a1f2b; color: #fff; width: fit-content; padding: 2px 16px; border-radius: 3px; }
      .bill__meta { display: flex; justify-content: space-between; gap: 16px; margin: 8px 0; }
      .label { color: #6b5f50; }
      .bill__lines { width: 100%; border-collapse: collapse; margin-bottom: 6px; }
      .bill__lines th, .bill__lines td { border: 1px solid #bfb3a0; padding: 3px 5px; text-align: right; font-variant-numeric: tabular-nums; }
      .bill__lines th { background: #f3ece0; text-align: center; }
      .left { text-align: left !important; } .center { text-align: center !important; } .right { text-align: right; }
      .strong { font-weight: 600; } .sub { font-size: 10px; color: #6b5f50; }
      .grand td { font-weight: 800; background: #f3ece0; }
      .bill__sign { display: flex; justify-content: space-between; margin-top: 40px; font-weight: 600; }
      @media print { .bill { border: 0; max-width: none; padding: 0; } }
    `,
  ],
})
export class OldMetalDetailComponent implements OnInit {
  private readonly api = inject(OldMetalApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  protected readonly permissions = Permissions;
  protected readonly bill = signal<OldMetalTransaction | null>(null);
  protected readonly cancelOpen = signal(false);
  protected readonly reason = signal('');
  protected readonly error = signal<string | null>(null);


  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    const autoPrint = this.route.snapshot.queryParamMap.get('print') === '1';
    this.api.get(id).subscribe({
      next: (bill) => {
        this.bill.set(bill);
        if (autoPrint) {
          void this.router.navigate([], { queryParams: {}, replaceUrl: true });
          setTimeout(() => window.print(), 300);
        }
      },
      error: () => void this.router.navigate(['/old-metal']),
    });
  }

  protected print(): void {
    window.print();
  }

  protected cancel(bill: OldMetalTransaction): void {
    this.error.set(null);
    this.api.cancel(bill.id, this.reason().trim()).subscribe({
      next: (updated) => {
        this.bill.set(updated);
        this.cancelOpen.set(false);
        this.notifications.success(`Purchase bill ${bill.transactionNumber} cancelled.`);
      },
      error: (error: unknown) =>
        this.error.set(error instanceof HttpErrorResponse && isApiError(error.error) ? error.error.message : 'Could not cancel.'),
    });
  }
}
