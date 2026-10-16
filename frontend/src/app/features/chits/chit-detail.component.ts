import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { Permissions } from '../../core/auth/permissions';
import { NotificationService } from '../../core/services/notification.service';
import { ModalComponent } from '../../shared/components/modal.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { SpinnerComponent } from '../../shared/components/spinner.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { isApiError } from '../../shared/models/api-error.model';
import { ChitAccount } from '../../shared/models/chit.model';
import { PAYMENT_METHODS, PaymentMethod } from '../../shared/models/sales.model';
import { InrPipe } from '../../shared/pipes/inr.pipe';
import { WeightPipe } from '../../shared/pipes/weight.pipe';
import { ChitApiService } from './chit-api.service';
import { ChitStatusBadgeComponent } from './chit-status-badge.component';

/**
 * One chit's passbook: what has gone in, month by month, and what is left to do.
 *
 * <p>On a gold chit every instalment carries the rate it bought at, because a
 * customer asking "why did last month get me less gold?" is asking about the
 * rate, and the answer has to be on the page.
 */
@Component({
  selector: 'app-chit-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    DatePipe,
    PageHeaderComponent,
    SpinnerComponent,
    ModalComponent,
    HasPermissionDirective,
    ChitStatusBadgeComponent,
    InrPipe,
    WeightPipe,
  ],
  template: `
    @if (chit(); as c) {
      <div class="page">
        <app-page-header [title]="'Chit ' + c.chitNumber" [subtitle]="c.customerName + ' · ' + c.schemeName">
          <a class="btn btn--ghost" routerLink="/chits">All chits</a>
          @if (c.status === 'ACTIVE') {
            <button *appHasPermission="permissions.CHIT_CREATE" type="button" class="btn btn--primary" (click)="openPay()">
              Take payment
            </button>
          }
          @if (c.status === 'COMPLETE') {
            <button *appHasPermission="permissions.CHIT_EDIT" type="button" class="btn btn--primary" (click)="openMature()">
              Add bonus &amp; mature
            </button>
          }
          @if (c.status === 'MATURED') {
            <a class="btn btn--primary" [routerLink]="['/sales/new']" [queryParams]="{ chit: c.id }">
              Buy jewellery with it
            </a>
            <button *appHasPermission="permissions.CHIT_EDIT" type="button" class="btn" (click)="openWithdraw()">
              Pay out as cash
            </button>
          }
          @if (c.status === 'ACTIVE' || c.status === 'COMPLETE') {
            <button *appHasPermission="permissions.CHIT_DELETE" type="button" class="btn btn--danger" (click)="cancelOpen.set(true)">
              Cancel chit
            </button>
          }
          <button *appHasPermission="permissions.CHIT_DELETE" type="button" class="btn btn--danger" (click)="deleteOpen.set(true)">
            Delete chit
          </button>
        </app-page-header>

        <div class="status-line">
          <app-chit-status-badge [status]="c.status" />
          <span class="text-muted">Started {{ c.startedOn | date: 'dd MMM yyyy' }}</span>
          @if (c.customerMobile) { <span class="text-muted">{{ c.customerMobile }}</span> }
          <span class="text-muted">{{ c.customerCode }}</span>
        </div>

        @if (c.status === 'COMPLETE') {
          <div class="alert alert--warning">
            @if (c.schemeType === 'GOLD') {
              Every month the customer owes is in. The shop's twelfth is still to be added, at the
              rate it buys at today.
            } @else {
              Every month the customer owes is in. The shop's twelfth is still to be added.
            }
          </div>
        }
        @if (c.status === 'MATURED') {
          <div class="alert alert--info">
            The chit is the customer's to spend. They can buy jewellery with it - the chit comes off
            the invoice and the jewellery is taxed as usual -
            @if (c.schemeType === 'GOLD') {
              or take the {{ c.balanceGrams | weight }} as cash, at today's rate.
            } @else {
              or take the {{ c.balanceAmount | inr: 'whole' }} as cash.
            }
          </div>
        }
        @if (c.status === 'CLOSED') {
          <div class="alert alert--info">
            @if (c.closeReason) {
              {{ c.closeReason }}, in full, on {{ c.closedOn | date: 'dd MMM yyyy' }}.
            } @else {
              Spent in full on {{ c.closedOn | date: 'dd MMM yyyy' }}.
            }
          </div>
        }
        @if (c.status === 'CANCELLED') {
          <div class="alert alert--warning">
            @if (c.closeReason) {
              Cancelled on {{ c.closedOn | date: 'dd MMM yyyy' }}: {{ c.closeReason }}
            } @else {
              Cancelled on {{ c.closedOn | date: 'dd MMM yyyy' }}.
            }
            What was paid in is settled at the counter.
          </div>
        }

        <section class="tiles">
          <div class="tile">
            <span class="tile__label">Monthly</span>
            <span class="tile__value">{{ c.monthlyAmount | inr: 'whole' }}</span>
          </div>
          <div class="tile">
            <span class="tile__label">Months paid</span>
            <span class="tile__value">
              {{ paidByCustomer(c) }} <small>of {{ c.payableInstalments }}</small>
            </span>
            @if (c.instalmentsOwed > 0) {
              <span class="tile__note">{{ c.instalmentsOwed }} still to come</span>
            } @else if (hasBonus(c)) {
              <span class="tile__note">Plus the shop's twelfth</span>
            }
          </div>
          <div class="tile">
            <span class="tile__label">Put in</span>
            <span class="tile__value">{{ c.paidAmount | inr: 'whole' }}</span>
            @if (c.remainingToPay > 0) {
              <span class="tile__note">{{ c.remainingToPay | inr: 'whole' }} left to pay</span>
            }
          </div>
          @if (c.schemeType === 'GOLD') {
            <div class="tile tile--gold">
              <span class="tile__label">Gold bought</span>
              <span class="tile__value">{{ c.accruedGrams | weight }}</span>
              <span class="tile__note">Balance {{ c.balanceGrams | weight }}</span>
            </div>
          } @else {
            <div class="tile tile--gold">
              <span class="tile__label">Worth at maturity</span>
              <span class="tile__value">{{ c.maturityAmount | inr: 'whole' }}</span>
              <span class="tile__note">Balance {{ c.balanceAmount | inr: 'whole' }}</span>
            </div>
          }
        </section>

        <section class="card">
          <div class="card__body">
            <h2 class="card__title">Passbook</h2>
          </div>
          @if (!c.payments.length) {
            <p class="card__body text-muted">No months paid yet.</p>
          } @else {
            <div class="table-wrap">
              <table class="table">
                <thead>
                  <tr>
                    <th>Month</th>
                    <th>Paid on</th>
                    <th class="num">Amount</th>
                    @if (c.schemeType === 'GOLD') {
                      <th class="num">Rate / g</th>
                      <th class="num">Gold</th>
                    }
                    <th>Method</th>
                    <th>Reference</th>
                    <th>Taken by</th>
                  </tr>
                </thead>
                <tbody>
                  @for (payment of c.payments; track payment.id) {
                    <tr [class.row--shop]="payment.paidByShop">
                      <td>
                        {{ payment.instalmentNo }}
                        @if (payment.paidByShop) { <span class="badge badge--success">Shop's bonus</span> }
                      </td>
                      <td>{{ payment.paidOn | date: 'dd MMM yyyy' }}</td>
                      <td class="num">{{ payment.amount | inr: 'whole' }}</td>
                      @if (c.schemeType === 'GOLD') {
                        <td class="num">{{ payment.ratePerGram | inr: 'whole' }}</td>
                        <td class="num">{{ payment.grams | weight }}</td>
                      }
                      <td>{{ payment.paidByShop ? '—' : payment.paymentMethod ?? '—' }}</td>
                      <td>{{ payment.referenceNo || '—' }}</td>
                      <td class="text-muted">{{ payment.createdBy }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </section>
        @if (c.redemptions.length) {
          <section class="card">
            <div class="card__body">
              <h2 class="card__title">Where it went</h2>
            </div>
            <div class="table-wrap">
              <table class="table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Taken as</th>
                    @if (c.schemeType === 'GOLD') {
                      <th class="num">Gold</th>
                      <th class="num">Rate / g</th>
                    }
                    <th class="num">Value</th>
                    <th>Taken by</th>
                  </tr>
                </thead>
                <tbody>
                  @for (out of c.redemptions; track out.id) {
                    <tr [class.row--reversed]="out.status === 'CANCELLED'">
                      <td>{{ out.redeemedOn | date: 'dd MMM yyyy' }}</td>
                      <td>
                        @if (out.kind === 'SALE') {
                          <a [routerLink]="['/sales', out.saleId]">{{ out.invoiceNumber }}</a>
                        } @else {
                          Cash{{ out.paymentMethod && out.paymentMethod !== 'CASH' ? ' (' + out.paymentMethod + ')' : '' }}
                        }
                        @if (out.status === 'CANCELLED') {
                          <span class="badge badge--muted">Put back</span>
                        }
                      </td>
                      @if (c.schemeType === 'GOLD') {
                        <td class="num">{{ out.grams | weight }}</td>
                        <td class="num">{{ out.ratePerGram | inr: 'whole' }}</td>
                      }
                      <td class="num">{{ out.amount | inr: 'whole' }}</td>
                      <td class="text-muted">{{ out.createdBy }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </section>
        }
      </div>

      @if (withdrawOpen()) {
        <app-modal [title]="'Pay out ' + c.chitNumber" (closed)="withdrawOpen.set(false)">
          <p>
            @if (c.schemeType === 'GOLD') {
              The customer takes the money instead of the gold. Their
              <strong>{{ c.balanceGrams | weight }}</strong> is sold at the rate below, and the chit closes.
            } @else {
              The customer takes <strong>{{ c.balanceAmount | inr: 'whole' }}</strong> and the chit closes.
            }
          </p>
          <div class="form-grid">
            <div class="field">
              <label class="field__label" for="withdrawnOn">Paid out on</label>
              <input id="withdrawnOn" class="input" type="date" [value]="withdrawnOn()" [max]="today"
                     (change)="withdrawnOn.set($any($event.target).value)" />
            </div>
            @if (c.schemeType === 'GOLD') {
              <div class="field">
                <label class="field__label field__label--required" for="outRate">Today's gold rate per gram</label>
                <input id="outRate" class="input" type="number" min="0.01" step="0.01" inputmode="decimal"
                       [value]="rate()" (input)="rate.set($any($event.target).value)" />
                <p class="field__hint">
                  @if (cashPreview(c); as value) {
                    {{ c.balanceGrams | weight }} comes to {{ value | inr: 'whole' }} at this rate.
                  } @else {
                    What the gold fetches today, which is what the customer is paid.
                  }
                </p>
              </div>
            }
            <div class="field">
              <label class="field__label" for="outMethod">Paid by</label>
              <select id="outMethod" class="select" (change)="method.set($any($event.target).value)">
                @for (option of paymentMethods; track option.value) {
                  <option [value]="option.value" [selected]="method() === option.value">{{ option.label }}</option>
                }
              </select>
            </div>
            <div class="field">
              <label class="field__label" for="outReference">Reference</label>
              <input id="outReference" class="input" maxlength="100" [value]="reference()"
                     (input)="reference.set($any($event.target).value)" />
            </div>
          </div>
          @if (error()) { <div class="alert alert--error">{{ error() }}</div> }
          <ng-container modalActions>
            <button type="button" class="btn btn--ghost" (click)="withdrawOpen.set(false)">Cancel</button>
            <button type="button" class="btn btn--primary" [disabled]="busy() || !payReady(c)" (click)="withdraw(c)">
              Pay out
              @if (c.schemeType !== 'GOLD') { {{ c.balanceAmount | inr: 'whole' }} }
              @else if (cashPreview(c); as value) { {{ value | inr: 'whole' }} }
            </button>
          </ng-container>
        </app-modal>
      }

      @if (payOpen()) {
        <app-modal [title]="'Month ' + (c.paidInstalments + 1) + ' of ' + c.payableInstalments" (closed)="payOpen.set(false)">
          <p>
            Taking <strong>{{ c.monthlyAmount | inr: 'whole' }}</strong> from {{ c.customerName }}.
            The amount is the scheme's, so it cannot be changed here.
          </p>
          <div class="form-grid">
            <div class="field">
              <label class="field__label" for="paidOn">Paid on</label>
              <input id="paidOn" class="input" type="date" [value]="paidOn()" [max]="today"
                     (change)="paidOn.set($any($event.target).value)" />
            </div>
            @if (c.schemeType === 'GOLD') {
              <div class="field">
                <label class="field__label field__label--required" for="rate">Today's gold rate per gram</label>
                <input id="rate" class="input" type="number" min="0.01" step="0.01" inputmode="decimal"
                       [value]="rate()" (input)="rate.set($any($event.target).value)" />
                <p class="field__hint">
                  @if (gramsPreview(); as grams) {
                    {{ c.monthlyAmount | inr: 'whole' }} buys {{ grams }} g at this rate.
                  } @else {
                    This month's rupees buy gold at this rate.
                  }
                </p>
              </div>
            }
            <div class="field">
              <label class="field__label" for="method">Method</label>
              <select id="method" class="select" (change)="method.set($any($event.target).value)">
                @for (option of paymentMethods; track option.value) {
                  <option [value]="option.value" [selected]="method() === option.value">{{ option.label }}</option>
                }
              </select>
            </div>
            <div class="field">
              <label class="field__label" for="reference">Reference</label>
              <input id="reference" class="input" maxlength="100" [value]="reference()"
                     (input)="reference.set($any($event.target).value)" />
            </div>
          </div>
          @if (error()) { <div class="alert alert--error">{{ error() }}</div> }
          <ng-container modalActions>
            <button type="button" class="btn btn--ghost" (click)="payOpen.set(false)">Cancel</button>
            <button type="button" class="btn btn--primary" [disabled]="busy() || !payReady(c)" (click)="pay(c)">
              Take {{ c.monthlyAmount | inr: 'whole' }}
            </button>
          </ng-container>
        </app-modal>
      }

      @if (matureOpen()) {
        <app-modal [title]="'Mature ' + c.chitNumber" (closed)="matureOpen.set(false)">
          <p>
            The shop puts in month {{ c.totalInstalments }} -
            <strong>{{ c.monthlyAmount | inr: 'whole' }}</strong> - and the balance becomes the
            customer's to spend or withdraw.
          </p>
          <div class="form-grid">
            <div class="field">
              <label class="field__label" for="maturedOn">Matured on</label>
              <input id="maturedOn" class="input" type="date" [value]="maturedOn()" [max]="today"
                     (change)="maturedOn.set($any($event.target).value)" />
            </div>
            @if (c.schemeType === 'GOLD') {
              <div class="field">
                <label class="field__label field__label--required" for="matureRate">Today's gold rate per gram</label>
                <input id="matureRate" class="input" type="number" min="0.01" step="0.01" inputmode="decimal"
                       [value]="rate()" (input)="rate.set($any($event.target).value)" />
                <p class="field__hint">The bonus buys gold at this rate, like any other month.</p>
              </div>
            }
          </div>
          @if (error()) { <div class="alert alert--error">{{ error() }}</div> }
          <ng-container modalActions>
            <button type="button" class="btn btn--ghost" (click)="matureOpen.set(false)">Not yet</button>
            <button type="button" class="btn btn--primary" [disabled]="busy() || !matureReady(c)" (click)="mature(c)">
              Add bonus &amp; mature
            </button>
          </ng-container>
        </app-modal>
      }

      @if (cancelOpen()) {
        <app-modal [title]="'Cancel ' + c.chitNumber" (closed)="cancelOpen.set(false)">
          <p>
            The chit stays on record, marked cancelled, as the account of what was paid in.
            Refunding {{ c.paidAmount | inr: 'whole' }} is settled at the counter.
          </p>
          <div class="field">
            <label class="field__label field__label--required" for="reason">Reason</label>
            <textarea id="reason" class="textarea" rows="3" maxlength="500"
                      (input)="reason.set($any($event.target).value)"></textarea>
          </div>
          @if (error()) { <div class="alert alert--error">{{ error() }}</div> }
          <ng-container modalActions>
            <button type="button" class="btn btn--ghost" (click)="cancelOpen.set(false)">Keep chit</button>
            <button type="button" class="btn btn--danger" [disabled]="busy() || !reason().trim()" (click)="cancel(c)">
              Cancel chit
            </button>
          </ng-container>
        </app-modal>
      }

      @if (deleteOpen()) {
        <app-modal [title]="'Delete ' + c.chitNumber" (closed)="deleteOpen.set(false)">
          <p>
            <strong>This erases the chit and every month on it.</strong> It will not appear in the
            books - unlike cancelling, which keeps it on record.
          </p>
          <p class="text-muted">
            {{ c.chitNumber }} is freed for the next chit if it was the last number issued.
            An older number stays spent.
          </p>
          <div class="field">
            <label class="field__label field__label--required" for="confirmNumber">
              Type {{ c.chitNumber }} to confirm
            </label>
            <input id="confirmNumber" class="input" autocomplete="off"
                   (input)="deleteConfirm.set($any($event.target).value)" />
          </div>
          @if (error()) { <div class="alert alert--error">{{ error() }}</div> }
          <ng-container modalActions>
            <button type="button" class="btn btn--ghost" (click)="deleteOpen.set(false)">Keep chit</button>
            <button type="button" class="btn btn--danger"
                    [disabled]="busy() || deleteConfirm().trim() !== c.chitNumber" (click)="remove(c)">
              Delete for good
            </button>
          </ng-container>
        </app-modal>
      }
    } @else {
      <app-spinner />
    }
  `,
  styles: [
    `
      .status-line { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); margin-bottom: var(--space-4); }
      .tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr)); gap: var(--space-3); margin-bottom: var(--space-4); }
      .tile {
        display: grid;
        gap: 2px;
        padding: var(--space-4);
        background: var(--surface-card);
        border: 1px solid var(--border-subtle);
        border-radius: var(--radius-md);
      }
      .tile--gold { background: var(--gold-soft); }
      .tile__label { color: var(--text-muted); font-size: var(--text-xs); text-transform: uppercase; letter-spacing: 0.04em; }
      .tile__value { font-size: var(--text-xl); font-weight: 700; }
      .tile__value small { font-size: var(--text-sm); font-weight: 400; color: var(--text-muted); }
      .tile__note { color: var(--text-secondary); font-size: var(--text-xs); }
      .form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr)); gap: var(--space-3); }
      .num { text-align: right; font-variant-numeric: tabular-nums; }
      .row--shop td { background: var(--surface-sunken); }
      .row--reversed td { color: var(--text-muted); text-decoration: line-through; }
      .table-wrap { overflow-x: auto; }
    `,
  ],
})
export class ChitDetailComponent implements OnInit {
  private readonly api = inject(ChitApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  protected readonly permissions = Permissions;
  protected readonly paymentMethods = PAYMENT_METHODS;
  protected readonly today = new Date().toISOString().slice(0, 10);

  protected readonly chit = signal<ChitAccount | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly payOpen = signal(false);
  protected readonly withdrawOpen = signal(false);
  protected readonly matureOpen = signal(false);
  protected readonly cancelOpen = signal(false);
  protected readonly deleteOpen = signal(false);
  protected readonly deleteConfirm = signal('');

  protected readonly paidOn = signal(this.today);
  protected readonly withdrawnOn = signal(this.today);
  protected readonly maturedOn = signal(this.today);
  protected readonly rate = signal('');
  protected readonly method = signal<PaymentMethod>('CASH');
  protected readonly reference = signal('');
  protected readonly reason = signal('');

  /**
   * What this month's rupees come to in gold, shown before the payment is
   * taken. Floored to a milligram, as the server does - a preview that rounded
   * up would read a milligram high against the passbook.
   */
  protected readonly gramsPreview = computed(() => {
    const chit = this.chit();
    const rate = Number(this.rate());
    if (!chit || !Number.isFinite(rate) || rate <= 0) {
      return null;
    }
    return (Math.floor((chit.monthlyAmount / rate) * 1000) / 1000).toFixed(3);
  });

  /**
   * Months the customer paid. The stored count takes in the shop's twelfth
   * once the chit matures, which is not something the customer paid.
   */
  protected paidByCustomer(chit: ChitAccount): number {
    return Math.min(chit.paidInstalments, chit.payableInstalments);
  }

  protected hasBonus(chit: ChitAccount): boolean {
    return chit.paidInstalments > chit.payableInstalments;
  }

  ngOnInit(): void {
    this.reload(Number(this.route.snapshot.paramMap.get('id')), true);
  }

  protected openPay(): void {
    this.resetForm();
    this.payOpen.set(true);
  }

  protected openMature(): void {
    this.resetForm();
    this.matureOpen.set(true);
  }

  protected openWithdraw(): void {
    this.resetForm();
    this.withdrawOpen.set(true);
  }

  /** What a gold chit's balance fetches at the rate typed in, before paying it out. */
  protected cashPreview(chit: ChitAccount): number | null {
    const rate = Number(this.rate());
    if (chit.schemeType !== 'GOLD' || !Number.isFinite(rate) || rate <= 0) {
      return null;
    }
    return Math.round(chit.balanceGrams * rate);
  }

  protected withdraw(chit: ChitAccount): void {
    this.busy.set(true);
    this.error.set(null);
    this.api
      .withdraw(chit.id, {
        withdrawnOn: this.withdrawnOn() || null,
        ratePerGram: chit.schemeType === 'GOLD' ? Number(this.rate()) : null,
        paymentMethod: this.method(),
        referenceNo: this.reference().trim() || null,
      })
      .subscribe({
        next: (updated) => {
          this.busy.set(false);
          this.withdrawOpen.set(false);
          this.chit.set(updated);
          this.notifications.success(
            `${updated.chitNumber} paid out and closed.`,
          );
        },
        error: (err: unknown) => this.fail(err, 'Could not pay the chit out. Try again.'),
      });
  }

  protected payReady(chit: ChitAccount): boolean {
    return chit.schemeType !== 'GOLD' || Number(this.rate()) > 0;
  }

  protected matureReady(chit: ChitAccount): boolean {
    return this.payReady(chit);
  }

  protected pay(chit: ChitAccount): void {
    this.busy.set(true);
    this.error.set(null);
    this.api
      .pay(chit.id, {
        paidOn: this.paidOn() || null,
        ratePerGram: chit.schemeType === 'GOLD' ? Number(this.rate()) : null,
        paymentMethod: this.method(),
        referenceNo: this.reference().trim() || null,
      })
      .subscribe({
        next: (updated) => {
          this.busy.set(false);
          this.payOpen.set(false);
          this.chit.set(updated);
          this.notifications.success(
            `Month ${updated.paidInstalments} of ${updated.payableInstalments} taken for ${updated.chitNumber}.`,
          );
        },
        error: (err: unknown) => this.fail(err, 'Could not take the payment. Try again.'),
      });
  }

  protected mature(chit: ChitAccount): void {
    this.busy.set(true);
    this.error.set(null);
    this.api
      .mature(chit.id, {
        maturedOn: this.maturedOn() || null,
        ratePerGram: chit.schemeType === 'GOLD' ? Number(this.rate()) : null,
      })
      .subscribe({
        next: (updated) => {
          this.busy.set(false);
          this.matureOpen.set(false);
          this.chit.set(updated);
          this.notifications.success(`${updated.chitNumber} matured. The bonus is in.`);
        },
        error: (err: unknown) => this.fail(err, 'Could not mature the chit. Try again.'),
      });
  }

  protected cancel(chit: ChitAccount): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.cancel(chit.id, this.reason().trim()).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.cancelOpen.set(false);
        this.chit.set(updated);
        this.notifications.success(`${updated.chitNumber} cancelled.`);
      },
      error: (err: unknown) => this.fail(err, 'Could not cancel the chit. Try again.'),
    });
  }

  protected remove(chit: ChitAccount): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.delete(chit.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.deleteOpen.set(false);
        this.notifications.success(`Chit ${chit.chitNumber} deleted.`);
        void this.router.navigate(['/chits']);
      },
      error: (err: unknown) => this.fail(err, 'Could not delete the chit. Try again.'),
    });
  }

  private fail(err: unknown, fallback: string): void {
    this.busy.set(false);
    this.error.set(
      err instanceof HttpErrorResponse && isApiError(err.error) ? err.error.message : fallback,
    );
  }

  private resetForm(): void {
    this.error.set(null);
    this.rate.set('');
    this.reference.set('');
    this.method.set('CASH');
    this.paidOn.set(this.today);
    this.maturedOn.set(this.today);
    this.withdrawnOn.set(this.today);
  }

  private reload(id: number, redirectOnFailure = false): void {
    this.api.findById(id).subscribe({
      next: (chit) => this.chit.set(chit),
      error: () => {
        if (redirectOnFailure) {
          void this.router.navigate(['/chits']);
        }
      },
    });
  }
}
