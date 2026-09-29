import { HttpContext, HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { Permissions } from '../../core/auth/permissions';
import { SUPPRESS_ERROR_TOAST } from '../../core/interceptors/error.interceptor';
import { NotificationService } from '../../core/services/notification.service';
import { ModalComponent } from '../../shared/components/modal.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { SpinnerComponent } from '../../shared/components/spinner.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { isApiError } from '../../shared/models/api-error.model';
import { PAYMENT_METHODS, PaymentMethod, Sale } from '../../shared/models/sales.model';
import { InrPipe } from '../../shared/pipes/inr.pipe';
import { InvoicePrintComponent } from './invoice-print.component';
import { SaleApiService } from './sales-api.service';
import { PaymentStatusBadgeComponent } from './status-badges.component';

@Component({
  selector: 'app-sale-detail',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    PageHeaderComponent,
    SpinnerComponent,
    ModalComponent,
    HasPermissionDirective,
    InrPipe,
    InvoicePrintComponent,
    PaymentStatusBadgeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (sale(); as s) {
      <div class="stack">
        <div class="no-print">
          <app-page-header [title]="'Invoice ' + s.invoiceNumber" [subtitle]="s.customerName + ' · ' + (s.invoiceDate | date: 'dd MMM yyyy')">
            <a class="btn btn--ghost" routerLink="/sales">All invoices</a>
            <button type="button" class="btn btn--primary" (click)="print()">Print</button>
            @if (s.status !== 'CANCELLED' && s.totals.balanceAmount > 0) {
              <button *appHasPermission="permissions.SALES_EDIT" type="button" class="btn" (click)="paymentOpen.set(true)">Record payment</button>
            }
            @if (s.status !== 'CANCELLED') {
              <button *appHasPermission="permissions.SALES_DELETE" type="button" class="btn btn--danger" (click)="cancelOpen.set(true)">Cancel invoice</button>
            }
          </app-page-header>
        </div>

        <div class="no-print summary">
          <app-payment-status-badge [status]="s.status === 'CANCELLED' ? 'CANCELLED' : s.totals.paymentStatus" />
          <span>Net payable <strong>{{ s.totals.netPayable | inr }}</strong></span>
          <span>Paid <strong>{{ s.totals.amountPaid | inr }}</strong></span>
          <span>Balance <strong>{{ s.totals.balanceAmount | inr }}</strong></span>
        </div>

        @if (s.status === 'CANCELLED') {
          <div class="alert alert--warning no-print">
            Cancelled by {{ s.cancelledBy }} on {{ s.cancelledAt | date: 'dd MMM yyyy, HH:mm' }}: {{ s.cancelReason }}.
            The pieces are back in stock and old gold value was returned to its bills. Payments already received are
            still listed below - settle any refund with the customer.
          </div>
        }

        <div class="print-area">
          <app-invoice-print [sale]="s" />
        </div>

        <section class="card no-print">
          <div class="card__header"><h2 class="card__title">Payments</h2></div>
          <div class="table-wrapper">
            <table class="table">
              <thead><tr><th>Date</th><th>Method</th><th>Reference</th><th>Recorded by</th><th class="td--numeric">Amount</th></tr></thead>
              <tbody>
                @for (payment of s.payments; track payment.id) {
                  <tr>
                    <td>{{ payment.paymentDate | date: 'dd-MM-yyyy' }}</td>
                    <td>{{ methodLabel(payment.method) }}</td>
                    <td>{{ payment.referenceNumber || '-' }}</td>
                    <td>{{ payment.createdBy || '-' }}</td>
                    <td class="td--numeric">{{ payment.amount | inr }}</td>
                  </tr>
                } @empty {
                  <tr><td colspan="5" class="text-muted">No payments recorded.</td></tr>
                }
              </tbody>
            </table>
          </div>
          @if (s.remarks) {
            <div class="card__body"><span class="text-muted">Remarks:</span> {{ s.remarks }}</div>
          }
        </section>
      </div>

      @if (paymentOpen()) {
        <app-modal title="Record payment" (closed)="paymentOpen.set(false)">
          <div class="form-grid">
            <div class="field">
              <label class="field__label" for="payMethod">Method</label>
              <select id="payMethod" class="select" (change)="payMethod.set($any($event.target).value)">
                @for (method of paymentMethods; track method.value) {
                  <option [value]="method.value" [selected]="method.value === payMethod()">{{ method.label }}</option>
                }
              </select>
            </div>
            <div class="field">
              <label class="field__label field__label--required" for="payAmount">Amount (balance {{ s.totals.balanceAmount | inr }})</label>
              <input id="payAmount" class="input numeric" type="number" min="0" step="0.01" [value]="payAmount()" (input)="payAmount.set($any($event.target).value)" />
            </div>
            <div class="field field--full">
              <label class="field__label" for="payRef">Reference</label>
              <input id="payRef" class="input" maxlength="100" (input)="payReference.set($any($event.target).value)" />
            </div>
            @if (dialogError()) { <div class="alert alert--error field--full">{{ dialogError() }}</div> }
          </div>
          <ng-container modalActions>
            <button type="button" class="btn btn--ghost" (click)="paymentOpen.set(false)">Close</button>
            <button type="button" class="btn btn--primary" [disabled]="busy()" (click)="recordPayment(s)">Save payment</button>
          </ng-container>
        </app-modal>
      }

      @if (cancelOpen()) {
        <app-modal [title]="'Cancel invoice ' + s.invoiceNumber" (closed)="cancelOpen.set(false)">
          <p>The invoice stays on record marked CANCELLED. Its pieces return to stock and any old gold value returns to the purchase bill.</p>
          <div class="field">
            <label class="field__label field__label--required" for="reason">Reason</label>
            <textarea id="reason" class="textarea" rows="3" maxlength="500" (input)="cancelReason.set($any($event.target).value)"></textarea>
          </div>
          @if (dialogError()) { <div class="alert alert--error">{{ dialogError() }}</div> }
          <ng-container modalActions>
            <button type="button" class="btn btn--ghost" (click)="cancelOpen.set(false)">Keep invoice</button>
            <button type="button" class="btn btn--danger" [disabled]="busy() || !cancelReason().trim()" (click)="cancel(s)">Cancel invoice</button>
          </ng-container>
        </app-modal>
      }
    } @else {
      <app-spinner />
    }
  `,
  styles: [
    `
      .summary { display: flex; flex-wrap: wrap; gap: var(--space-3) var(--space-6); align-items: center; }
      .print-area { overflow-x: auto; }
    `,
  ],
})
export class SaleDetailComponent implements OnInit {
  private readonly api = inject(SaleApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  protected readonly permissions = Permissions;
  protected readonly paymentMethods = PAYMENT_METHODS;
  protected readonly sale = signal<Sale | null>(null);
  protected readonly paymentOpen = signal(false);
  protected readonly cancelOpen = signal(false);
  protected readonly busy = signal(false);
  protected readonly dialogError = signal<string | null>(null);
  protected readonly payMethod = signal<PaymentMethod>('CASH');
  protected readonly payAmount = signal('');
  protected readonly payReference = signal('');
  protected readonly cancelReason = signal('');

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    const autoPrint = this.route.snapshot.queryParamMap.get('print') === '1';
    this.api.get(id).subscribe({
      next: (sale) => {
        this.sale.set(sale);
        if (autoPrint) {
          void this.router.navigate([], { queryParams: {}, replaceUrl: true });
          setTimeout(() => window.print(), 300);
        }
      },
      error: () => void this.router.navigate(['/sales']),
    });
  }

  protected print(): void {
    window.print();
  }

  protected methodLabel(method: PaymentMethod): string {
    return PAYMENT_METHODS.find((m) => m.value === method)?.label ?? method;
  }

  protected recordPayment(sale: Sale): void {
    const amount = Number(this.payAmount());
    if (!amount || amount <= 0) {
      this.dialogError.set('Enter an amount greater than zero.');
      return;
    }
    this.run(
      this.api.addPayment(
        sale.id,
        { method: this.payMethod(), amount, referenceNumber: this.payReference().trim() || null },
        new HttpContext().set(SUPPRESS_ERROR_TOAST, true),
      ),
      'Payment recorded.',
      () => this.paymentOpen.set(false),
    );
  }

  protected cancel(sale: Sale): void {
    this.run(this.api.cancel(sale.id, this.cancelReason().trim()), `Invoice ${sale.invoiceNumber} cancelled.`, () =>
      this.cancelOpen.set(false),
    );
  }

  private run(call: import('rxjs').Observable<Sale>, message: string, done: () => void): void {
    this.busy.set(true);
    this.dialogError.set(null);
    call.subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.sale.set(updated);
        this.payAmount.set('');
        this.notifications.success(message);
        done();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.dialogError.set(
          error instanceof HttpErrorResponse && isApiError(error.error) ? error.error.message : 'Could not save. Try again.',
        );
      },
    });
  }
}
