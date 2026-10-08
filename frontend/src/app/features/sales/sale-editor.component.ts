import { HttpContext, HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
  viewChild,
  viewChildren,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, switchMap, catchError, of } from 'rxjs';

import { SUPPRESS_ERROR_TOAST } from '../../core/interceptors/error.interceptor';
import { NotificationService } from '../../core/services/notification.service';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { isApiError } from '../../shared/models/api-error.model';
import {
  CustomerSummary,
  OldMetalOption,
  PAYMENT_METHODS,
  PaymentMethod,
  SaleCalculation,
  SaleItemLookup,
  SaleLine,
  SaleRequest,
} from '../../shared/models/sales.model';
import { InrPipe } from '../../shared/pipes/inr.pipe';
import { WeightPipe } from '../../shared/pipes/weight.pipe';
import { CustomerPickerComponent } from '../customers/customer-picker.component';
import { OldMetalApiService, SaleApiService } from './sales-api.service';

/** One row of the particulars table, as typed at the counter. */
interface Row {
  key: number;
  serial: string;
  lookup: SaleItemLookup | null;
  lookupError: string | null;
  looking: boolean;
  particulars: string;
  /** Only used for a bulk box: the grams the counter weighed out. */
  weight: string;
  wastage: string;
  rate: string;
  making: string;
}

interface PaymentRow {
  key: number;
  method: PaymentMethod;
  amount: string;
  reference: string;
}

const quiet = () => new HttpContext().set(SUPPRESS_ERROR_TOAST, true);

function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse && isApiError(error.error)) {
    const fields = Object.values(error.error.fieldErrors ?? {});
    return fields.length ? fields.join(' ') : error.error.message;
  }
  return 'Something went wrong. Please try again.';
}

function toNumber(value: string): number | null {
  if (value === null || value === undefined || value.trim() === '') {
    return null;
  }
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

/**
 * The sale counter.
 *
 * The salesperson types a serial number; the server answers with the piece's
 * category, sub category, HSN code, GST rate and net weight. They then key in
 * what the paper receipt has them write by hand - wastage %, rate, making charge,
 * discount - and every figure is priced by the server (POST /sales/calculate) as
 * they type. Nothing on this screen does billing arithmetic itself.
 *
 * A bulk box - metti and the like - is the one piece whose weight is not read
 * from the tag: the customer takes four out of a hundred, the shop weighs what
 * they took, and that is typed here. The box keeps its serial and stays on the
 * invoice list until the last gram of it has been sold.
 */
@Component({
  selector: 'app-sale-editor',
  standalone: true,
  imports: [RouterLink, PageHeaderComponent, CustomerPickerComponent, InrPipe, WeightPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-header title="New sale" subtitle="Enter each piece's serial number - its details load automatically.">
        <a class="btn btn--ghost" routerLink="/sales">All invoices</a>
      </app-page-header>

      <div class="layout">
        <div class="stack">
          <!-- Customer -->
          <section class="card">
            <div class="card__header"><h2 class="card__title">Customer</h2></div>
            <div class="card__body grid-2">
              <div class="field field--full-mobile">
                <app-customer-picker [(customer)]="customer" />
              </div>
              <div class="field">
                <label class="field__label" for="invoiceDate">Invoice date</label>
                <input id="invoiceDate" class="input" type="date" [max]="today" [value]="invoiceDate()"
                       (change)="invoiceDate.set($any($event.target).value); schedule()" />
              </div>
            </div>
          </section>

          <!-- Particulars -->
          <section class="card">
            <div class="card__header">
              <h2 class="card__title">Particulars</h2>
              <div class="mode" role="group" aria-label="How items are added">
                <button type="button" class="mode__btn" [class.mode__btn--on]="entryMode() === 'SCAN'"
                        [attr.aria-pressed]="entryMode() === 'SCAN'" (click)="setMode('SCAN')">Scan barcode</button>
                <button type="button" class="mode__btn" [class.mode__btn--on]="entryMode() === 'MANUAL'"
                        [attr.aria-pressed]="entryMode() === 'MANUAL'" (click)="setMode('MANUAL')">Manual entry</button>
              </div>
              @if (entryMode() === 'MANUAL') {
                <button type="button" class="btn btn--sm" (click)="addRow()">+ Add item</button>
              }
            </div>

            @if (entryMode() === 'SCAN') {
              <div class="scan" [class.scan--error]="scanError()">
                <label class="field__label" for="scanBox">Scan a tag</label>
                <input #scanBox id="scanBox" class="input scan__input" inputmode="numeric" maxlength="6"
                       autocomplete="off" placeholder="Pull the scanner trigger" [value]="scanValue()"
                       (input)="onScanInput($any($event.target).value)" (keydown)="onScanKey($event)" />
                <p class="scan__hint" aria-live="polite">
                  @if (scanError()) {
                    <span class="text-danger">{{ scanError() }}</span>
                  } @else if (scanning()) {
                    Looking up {{ scanValue() }}...
                  } @else if (scanStatus()) {
                    {{ scanStatus() }}
                  } @else {
                    Ready. Each scan adds the piece below - scan them all, then fill in the rates.
                  }
                </p>
              </div>
            }
            <div class="lines">
              @if (!rows().length) {
                <p class="lines__empty">No pieces yet. Scan the tag on the first item.</p>
              }
              @for (row of rows(); track row.key; let i = $index) {
                <div class="line" [class.line--error]="row.lookupError">
                  <div class="line__head">
                    <span class="line__no">{{ i + 1 }}</span>
                    <div class="field serial-field">
                      <label class="field__label field__label--required" [for]="'serial' + row.key">Serial no</label>
                      <input #serialInput class="input serial" [id]="'serial' + row.key" inputmode="numeric" maxlength="6"
                             placeholder="000123" autocomplete="off" [value]="row.serial"
                             [readonly]="entryMode() === 'SCAN'"
                             (input)="setSerial(i, $any($event.target).value)"
                             (keydown)="onSerialKey($event, i)"
                             (blur)="lookup(i)" />
                    </div>
                    @if (row.lookup; as item) {
                      <div class="field particulars-field">
                        <label class="field__label" [for]="'part' + row.key">Particulars</label>
                        <input class="input" [id]="'part' + row.key" maxlength="200" [value]="row.particulars"
                               (input)="patch(i, { particulars: $any($event.target).value })" />
                      </div>
                    }
                    <button type="button" class="btn btn--ghost btn--icon line__remove" aria-label="Remove item" (click)="removeRow(i)">&times;</button>
                  </div>

                  @if (row.looking) {
                    <p class="text-muted">Looking up serial {{ row.serial }}...</p>
                  }
                  @if (row.lookupError) {
                    <p class="field__error">{{ row.lookupError }}</p>
                  }

                  @if (row.lookup; as item) {
                    <div class="facts">
                      <span><b>Category</b> {{ item.categoryName }}</span>
                      <span><b>Sub category</b> {{ item.subCategoryName || '-' }}</span>
                      <span><b>Type</b> {{ item.itemTypeName }} {{ item.purityName }}</span>
                      <span><b>HSN</b> {{ item.hsnCode }} ({{ item.gstPercentage }}% GST)</span>
                      @if (item.bulk) {
                        <span class="facts__bulk"><b>Bulk</b> {{ item.netWeightGrams | weight }} left in the box</span>
                      } @else {
                        <span><b>Net wt</b> {{ item.netWeightGrams | weight }}</span>
                      }
                    </div>
                    <div class="inputs">
                      @if (item.bulk) {
                        <div class="field">
                          <label class="field__label field__label--required" [for]="'g' + row.key">Weight sold (g)</label>
                          <input class="input numeric" [id]="'g' + row.key" type="number" min="0.001" step="0.001"
                                 [max]="item.netWeightGrams" [value]="row.weight"
                                 [class.input--invalid]="overWeight(row)"
                                 (input)="patch(i, { weight: $any($event.target).value })" />
                        </div>
                      }
                      <div class="field">
                        <label class="field__label" [for]="'w' + row.key">Wastage %</label>
                        <input class="input numeric" [id]="'w' + row.key" type="number" min="0" max="100" step="0.01"
                               [value]="row.wastage" (input)="patch(i, { wastage: $any($event.target).value })" />
                      </div>
                      <div class="field">
                        <label class="field__label field__label--required" [for]="'r' + row.key">Rate / g</label>
                        <input class="input numeric" [id]="'r' + row.key" type="number" min="0" step="0.01"
                               [value]="row.rate" (input)="patch(i, { rate: $any($event.target).value })" />
                      </div>
                      <div class="field">
                        <label class="field__label" [for]="'m' + row.key">Making charge</label>
                        <input class="input numeric" [id]="'m' + row.key" type="number" min="0" step="0.01"
                               [value]="row.making" (input)="patch(i, { making: $any($event.target).value })" />
                      </div>
                      @if (priced(row.serial); as line) {
                        <div class="figure-cell"><span class="field__label">Wastage wt</span>{{ line.wastageWeightGrams | weight }}</div>
                        <div class="figure-cell"><span class="field__label">Gross wt</span>{{ line.grossWeightGrams | weight }}</div>
                        <div class="figure-cell figure-cell--amount"><span class="field__label">Amount</span>{{ line.amount | inr: 'whole' }}</div>
                      }
                    </div>
                    @if (overWeight(row)) {
                      <p class="field__error">
                        Only {{ item.netWeightGrams | weight }} is left in this box.
                      </p>
                    } @else if (item.bulk && !row.weight.trim()) {
                      <p class="field__hint">Weigh what the customer is taking and enter it above.</p>
                    }
                  }
                </div>
              }
            </div>
          </section>

          <!-- Old gold / silver -->
          @if (customer()) {
            <section class="card">
              <div class="card__header"><h2 class="card__title">Old gold / silver adjustment</h2></div>
              <div class="card__body">
                @if (oldMetalOptions().length === 0) {
                  <p class="text-muted">This customer has no purchase bills with value left to use.</p>
                } @else {
                  <div class="table-wrapper">
                    <table class="table">
                      <thead>
                        <tr><th></th><th>Bill</th><th>Details</th><th class="td--numeric">Available</th><th class="td--numeric">Use</th></tr>
                      </thead>
                      <tbody>
                        @for (option of oldMetalOptions(); track option.id) {
                          <tr>
                            <td><input type="checkbox" [checked]="adjustments()[option.id] !== undefined" (change)="toggleAdjustment(option)" [attr.aria-label]="'Use ' + option.transactionNumber" /></td>
                            <td class="table__serial">{{ option.transactionNumber }}</td>
                            <td>{{ option.description }}</td>
                            <td class="td--numeric">{{ option.availableAmount | inr }}</td>
                            <td class="td--numeric">
                              @if (adjustments()[option.id] !== undefined) {
                                <input class="input numeric amount-input" type="number" min="0" step="0.01" [max]="option.availableAmount"
                                       [value]="adjustments()[option.id]" (input)="setAdjustment(option.id, $any($event.target).value)" />
                              }
                            </td>
                          </tr>
                        }
                      </tbody>
                    </table>
                  </div>
                }
              </div>
            </section>
          }

          <!-- Payments -->
          <section class="card">
            <div class="card__header">
              <h2 class="card__title">Payment received</h2>
              <div class="row">
                @if (calculation(); as calc) {
                  <button type="button" class="btn btn--sm" (click)="payInFull()">Paid in full</button>
                }
                <button type="button" class="btn btn--sm" (click)="addPayment()">+ Add payment</button>
              </div>
            </div>
            <div class="card__body stack">
              @for (payment of payments(); track payment.key; let p = $index) {
                <div class="payment">
                  <select class="select" [value]="payment.method" (change)="patchPayment(p, { method: $any($event.target).value })" aria-label="Payment method">
                    @for (method of paymentMethods; track method.value) {
                      <option [value]="method.value" [selected]="method.value === payment.method">{{ method.label }}</option>
                    }
                  </select>
                  <input class="input numeric" type="number" min="0" step="0.01" placeholder="Amount" [value]="payment.amount"
                         (input)="patchPayment(p, { amount: $any($event.target).value })" aria-label="Amount" />
                  <input class="input" placeholder="Reference (UPI / card)" maxlength="100" [value]="payment.reference"
                         (input)="patchPayment(p, { reference: $any($event.target).value })" aria-label="Reference" />
                  <button type="button" class="btn btn--ghost btn--icon" aria-label="Remove payment" (click)="removePayment(p)">&times;</button>
                </div>
              } @empty {
                <p class="text-muted">No payment yet - the invoice will be saved with the full amount outstanding.</p>
              }
            </div>
          </section>

          <section class="card">
            <div class="card__body field">
              <label class="field__label" for="remarks">Remarks</label>
              <textarea id="remarks" class="textarea" rows="2" maxlength="500" [value]="remarks()" (input)="remarks.set($any($event.target).value)"></textarea>
            </div>
          </section>
        </div>

        <!-- Totals -->
        <aside class="card totals">
          <div class="card__header"><h2 class="card__title">Invoice total</h2></div>
          <div class="card__body">
            @if (calculation(); as calc) {
              <dl class="sums">
                <dt>Total</dt><dd>{{ calc.totals.subtotal | inr }}</dd>
                <dt>CGST</dt><dd>{{ calc.totals.cgstAmount | inr }}</dd>
                <dt>SGST</dt><dd>{{ calc.totals.sgstAmount | inr }}</dd>
                <dt><label for="discount">Discount</label></dt>
                <dd><input id="discount" class="input numeric amount-input" type="number" min="0" step="0.01" [value]="discount()"
                           (input)="discount.set($any($event.target).value); schedule()" /></dd>
                <dt class="sums__grand">Grand total</dt><dd class="sums__grand">{{ calc.totals.grandTotal | inr }}</dd>
                <dd class="words">{{ calc.totals.grandTotalInWords }}</dd>
                @if (calc.totals.oldMetalAdjustmentAmount > 0) {
                  <dt>Less old gold / silver</dt><dd>- {{ calc.totals.oldMetalAdjustmentAmount | inr }}</dd>
                }
                @if (calc.totals.roundOffAmount !== 0) {
                  <dt>Round off</dt><dd>{{ calc.totals.roundOffAmount | inr }}</dd>
                }
                <dt class="sums__grand">Net payable</dt><dd class="sums__grand">{{ calc.totals.netPayable | inr }}</dd>
                <dt>Paid</dt><dd>{{ calc.totals.amountPaid | inr }}</dd>
                <dt>Balance</dt><dd [class.text-danger]="calc.totals.balanceAmount > 0">{{ calc.totals.balanceAmount | inr }}</dd>
              </dl>
            } @else {
              <p class="text-muted">Enter a serial number and rate to see the invoice total.</p>
              <div class="field">
                <label class="field__label" for="discount0">Discount</label>
                <input id="discount0" class="input numeric" type="number" min="0" step="0.01" [value]="discount()"
                       (input)="discount.set($any($event.target).value); schedule()" />
              </div>
            }

            @if (calculating()) {
              <p class="text-muted">Calculating...</p>
            }
            @if (calcError()) {
              <div class="alert alert--error">{{ calcError() }}</div>
            }
            @if (saveError()) {
              <div class="alert alert--error">{{ saveError() }}</div>
            }

            <button type="button" class="btn btn--primary btn--block" [disabled]="!canSave()" (click)="save()">
              {{ saving() ? 'Saving...' : 'Save & print invoice' }}
            </button>
            @if (!customer()) {
              <p class="field__hint">Choose a customer to save.</p>
            }
          </div>
        </aside>
      </div>
    </div>
  `,
  host: { '(document:keydown)': 'onAnywhereKey($event)' },
  styles: [
    `
      .layout { display: grid; grid-template-columns: minmax(0, 1fr) 20rem; gap: var(--space-4); align-items: start; }
      .totals { position: sticky; top: calc(var(--header-height) + var(--space-4)); }
      @media (max-width: 1100px) { .layout { grid-template-columns: 1fr; } .totals { position: static; } }
      .grid-2 { display: grid; grid-template-columns: minmax(0, 2fr) minmax(10rem, 1fr); gap: var(--space-4); }
      @media (max-width: 640px) { .grid-2 { grid-template-columns: 1fr; } }
      .lines { display: flex; flex-direction: column; }
      .lines__empty { margin: 0; padding: var(--space-5); color: var(--text-muted); border-top: 1px solid var(--border-subtle); }
      .mode { display: inline-flex; margin-left: auto; border: 1px solid var(--border-subtle); border-radius: var(--radius-sm); overflow: hidden; }
      .mode__btn { padding: var(--space-1) var(--space-3); font: inherit; font-size: var(--text-sm); background: none; border: 0; cursor: pointer; color: var(--text-secondary); }
      .mode__btn--on { background: var(--brand); color: #fff; font-weight: 600; }
      .scan { padding: var(--space-4) var(--space-5); border-top: 1px solid var(--border-subtle); background: var(--surface-subtle, transparent); }
      .scan--error .scan__input { border-color: var(--danger); }
      .scan__input { max-width: 16rem; font-size: var(--text-lg); font-variant-numeric: tabular-nums; letter-spacing: 0.08em; }
      .scan__hint { margin: var(--space-2) 0 0; font-size: var(--text-sm); color: var(--text-muted); min-height: 1.2em; }
      .line { padding: var(--space-4) var(--space-5); border-top: 1px solid var(--border-subtle); display: flex; flex-direction: column; gap: var(--space-3); }
      .line--error { background: var(--danger-soft); }
      .line__head { display: flex; align-items: flex-end; gap: var(--space-3); flex-wrap: wrap; }
      .line__no { font-weight: 600; color: var(--text-muted); padding-bottom: var(--space-2); min-width: 1.5rem; }
      .line__remove { margin-left: auto; }
      .serial-field { width: 8rem; }
      .serial { font-family: var(--font-mono); letter-spacing: 0.08em; }
      .particulars-field { flex: 1; min-width: 12rem; }
      .facts { display: flex; flex-wrap: wrap; gap: var(--space-2) var(--space-5); font-size: var(--text-sm); color: var(--text-secondary); }
      .facts b { color: var(--text-muted); font-weight: 500; margin-right: var(--space-1); }
      /* A box is the one line whose weight the counter has to supply, so say so. */
      .facts__bulk { color: var(--text-primary); font-weight: 600; }
      .inputs { display: grid; grid-template-columns: repeat(auto-fit, minmax(7.5rem, 1fr)); gap: var(--space-3); align-items: end; }
      .figure-cell { display: flex; flex-direction: column; font-variant-numeric: tabular-nums; padding-bottom: var(--space-2); }
      .figure-cell--amount { font-weight: 700; font-size: var(--text-md); }
      .payment { display: grid; grid-template-columns: 9rem 9rem minmax(0, 1fr) auto; gap: var(--space-2); }
      @media (max-width: 640px) { .payment { grid-template-columns: 1fr 1fr; } }
      .amount-input { width: 8rem; margin-left: auto; }
      .sums { display: grid; grid-template-columns: 1fr auto; gap: var(--space-2) var(--space-3); margin: 0 0 var(--space-4); align-items: center; }
      .sums dt { color: var(--text-secondary); }
      .sums dd { margin: 0; text-align: right; font-variant-numeric: tabular-nums; }
      .sums__grand { font-weight: 700; font-size: var(--text-md); color: var(--text-primary) !important; border-top: 1px solid var(--border-subtle); padding-top: var(--space-2); }
      .words { grid-column: 1 / -1; font-size: var(--text-sm); font-style: italic; color: var(--text-muted); text-align: right; }
      .text-danger { color: var(--danger); font-weight: 600; }
    `,
  ],
})
export class SaleEditorComponent {
  private readonly sales = inject(SaleApiService);
  private readonly oldMetal = inject(OldMetalApiService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly serialInputs = viewChildren<ElementRef<HTMLInputElement>>('serialInput');
  private readonly scanBox = viewChild<ElementRef<HTMLInputElement>>('scanBox');
  private readonly recalc$ = new Subject<void>();
  private nextKey = 1;

  protected readonly paymentMethods = PAYMENT_METHODS;
  protected readonly today = new Date().toLocaleDateString('en-CA');

  protected readonly customer = signal<CustomerSummary | null>(null);
  protected readonly invoiceDate = signal(this.today);
  protected readonly rows = signal<Row[]>([]);
  protected readonly discount = signal('');
  protected readonly remarks = signal('');
  protected readonly payments = signal<PaymentRow[]>([]);
  protected readonly oldMetalOptions = signal<OldMetalOption[]>([]);
  /** Bill id -> amount typed, for the bills ticked. */
  protected readonly adjustments = signal<Record<number, string>>({});

  protected readonly calculation = signal<SaleCalculation | null>(null);
  protected readonly calculating = signal(false);
  protected readonly calcError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);

  /**
   * How pieces get onto the bill. Scanning is the default: the counter has a
   * barcode scanner and typing six digits per piece is the slow path.
   */
  protected readonly entryMode = signal<'SCAN' | 'MANUAL'>('SCAN');
  protected readonly scanValue = signal('');
  protected readonly scanStatus = signal<string | null>(null);
  protected readonly scanError = signal<string | null>(null);
  protected readonly scanning = signal(false);
  /** Set when a scanner sends no Enter after the digits; see {@link onScanInput}. */
  private scanIdle?: ReturnType<typeof setTimeout>;

  private readonly pricedBySerial = computed(() => {
    const map = new Map<string, SaleLine>();
    for (const line of this.calculation()?.lines ?? []) {
      map.set(line.serialNumber, line);
    }
    return map;
  });

  protected readonly canSave = computed(
    () =>
      !this.saving() &&
      !this.calculating() &&
      !!this.customer() &&
      !!this.calculation() &&
      !this.calcError() &&
      this.readyRows().length > 0 &&
      this.readyRows().length === this.rows().filter((row) => row.serial.trim() !== '').length,
  );

  private readonly readyRows = computed(() =>
    this.rows().filter(
      (row) => row.lookup && (toNumber(row.rate) ?? 0) > 0 && this.weightSettled(row),
    ),
  );

  /**
   * Whether a row's weight is known.
   *
   * <p>A single piece weighs what its tag says. A bulk box weighs whatever the
   * counter put on the scale, so until that is typed the line cannot be priced -
   * and asking for more than the box holds is refused here as well as by the
   * server, so the invoice is not sent only to bounce.
   */
  private weightSettled(row: Row): boolean {
    if (!row.lookup?.bulk) {
      return true;
    }
    const grams = toNumber(row.weight) ?? 0;
    return grams > 0 && grams <= row.lookup.netWeightGrams;
  }

  /** More grams asked of a box than are left in it - shown on the row as it is typed. */
  protected overWeight(row: Row): boolean {
    const grams = toNumber(row.weight) ?? 0;
    return !!row.lookup?.bulk && grams > row.lookup.netWeightGrams;
  }

  constructor() {
    this.recalc$
      .pipe(
        debounceTime(350),
        switchMap(() => {
          const request = this.buildRequest();
          if (request.items.length === 0) {
            this.calculating.set(false);
            return of(null);
          }
          this.calculating.set(true);
          return this.sales.calculate(request, quiet()).pipe(
            catchError((error) => {
              this.calcError.set(errorMessage(error));
              return of(null);
            }),
          );
        }),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((result) => {
        this.calculating.set(false);
        this.calculation.set(result);
        if (result) {
          this.calcError.set(null);
        }
      });

    // A new customer means different old gold bills.
    effect(() => {
      const selected = this.customer();
      this.adjustments.set({});
      this.oldMetalOptions.set([]);
      if (selected) {
        this.oldMetal.usable(selected.id).subscribe((options) => this.oldMetalOptions.set(options));
      }
      this.schedule();
    });

    // The counter's first move is to scan, so the box is ready for a trigger pull.
    this.focusScanBox();
  }

  protected priced(serial: string): SaleLine | undefined {
    return this.pricedBySerial().get(serial.trim().padStart(3, '0'));
  }

  protected schedule(): void {
    this.saveError.set(null);
    this.recalc$.next();
  }

  // --- scanning ---------------------------------------------------------------

  protected setMode(mode: 'SCAN' | 'MANUAL'): void {
    if (this.entryMode() === mode) {
      return;
    }
    this.entryMode.set(mode);
    this.scanError.set(null);
    this.scanStatus.set(null);
    this.scanValue.set('');
    if (mode === 'SCAN') {
      // Drop the half-typed row manual entry leaves behind; scanning replaces it.
      this.rows.update((rows) => rows.filter((row) => row.lookup));
      this.focusScanBox();
    } else if (!this.rows().length) {
      this.rows.set([this.emptyRow()]);
    }
    this.schedule();
  }

  /** A scanner configured with an Enter suffix ends the scan here. */
  protected onScanKey(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === 'Return' || event.keyCode === 13) {
      event.preventDefault();
      this.commitScan();
    }
  }

  protected onScanInput(value: string): void {
    const digits = value.replace(/D/g, '').slice(0, 6);
    this.scanValue.set(digits);
    this.scanError.set(null);
    clearTimeout(this.scanIdle);
    // Not every scanner is set up to send an Enter after the digits. One that is
    // not simply stops typing, so a pause in a burst this fast is the end of it.
    if (digits.length >= 4) {
      this.scanIdle = setTimeout(() => this.commitScan(), 200);
    }
  }

  /**
   * Keeps scanning working when the cursor is somewhere else on the page.
   *
   * <p>The scanner is a keyboard: whatever has focus receives the digits. Rather
   * than let a scan land in the wastage box, the first character of one pulls
   * focus to the scan box and is typed there instead. Anything typed inside a
   * field is left alone - that is a person at the keyboard, not a trigger pull.
   */
  protected onAnywhereKey(event: KeyboardEvent): void {
    if (this.entryMode() !== 'SCAN' || event.ctrlKey || event.altKey || event.metaKey) {
      return;
    }
    const target = event.target as HTMLElement | null;
    const tag = target?.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target?.isContentEditable) {
      return;
    }
    if (!/^[0-9]$/.test(event.key)) {
      return;
    }
    event.preventDefault();
    this.scanBox()?.nativeElement.focus();
    this.onScanInput(this.scanValue() + event.key);
  }

  private commitScan(): void {
    clearTimeout(this.scanIdle);
    const serial = this.scanValue().trim();
    if (!serial || this.scanning()) {
      return;
    }
    const canonical = serial.padStart(3, '0');
    if (this.rows().some((row) => row.lookup?.serialNumber === canonical)) {
      this.scanValue.set('');
      this.scanStatus.set(null);
      this.scanError.set(`Serial ${canonical} is already on this invoice.`);
      this.focusScanBox();
      return;
    }

    this.scanning.set(true);
    this.scanStatus.set(null);
    this.sales.lookupItem(serial, quiet()).subscribe({
      next: (item) => {
        const last = this.rows().at(-1);
        const row = this.emptyRow();
        row.serial = item.serialNumber;
        row.lookup = item;
        row.particulars = item.suggestedParticulars ?? '';
        // The day's rate is the same for every piece on the bill, so carry it over.
        row.rate = last?.rate ?? '';
        row.wastage = last?.wastage ?? '';
        this.rows.update((rows) => [...rows, row]);

        this.scanning.set(false);
        this.scanValue.set('');
        this.scanError.set(null);
        this.scanStatus.set(
          item.bulk
            ? `Added ${item.serialNumber} - bulk. Enter the weight being sold.`
            : `Added ${item.serialNumber} - ${item.suggestedParticulars || item.categoryName}`,
        );
        this.focusScanBox();
        this.schedule();
      },
      error: (error) => {
        this.scanning.set(false);
        this.scanValue.set('');
        this.scanStatus.set(null);
        this.scanError.set(errorMessage(error));
        this.focusScanBox();
      },
    });
  }

  private focusScanBox(): void {
    setTimeout(() => this.scanBox()?.nativeElement.focus());
  }

  // --- rows -----------------------------------------------------------------

  protected addRow(focus = true): void {
    const last = this.rows().at(-1);
    const row = this.emptyRow();
    // The day's gold rate is usually the same for every piece on the bill.
    row.rate = last?.rate ?? '';
    row.wastage = last?.wastage ?? '';
    this.rows.update((rows) => [...rows, row]);
    if (focus) {
      setTimeout(() => this.serialInputs().at(-1)?.nativeElement.focus());
    }
  }

  protected removeRow(index: number): void {
    this.rows.update((rows) => {
      const next = rows.filter((_, i) => i !== index);
      // Manual entry always leaves somewhere to type; scanning does not need it.
      return next.length || this.entryMode() === 'SCAN' ? next : [this.emptyRow()];
    });
    if (this.entryMode() === 'SCAN') {
      this.focusScanBox();
    }
    this.schedule();
  }

  protected patch(index: number, changes: Partial<Row>): void {
    this.rows.update((rows) => rows.map((row, i) => (i === index ? { ...row, ...changes } : row)));
    this.schedule();
  }

  protected setSerial(index: number, value: string): void {
    const row = this.rows()[index];
    const digits = value.replace(/\D/g, '');
    // Editing the serial invalidates what was loaded for the old one.
    this.patch(index, { serial: digits, lookup: row.lookup && row.lookup.serialNumber === digits.padStart(3, '0') ? row.lookup : null, lookupError: null });
  }

  /** Enter (or a barcode scanner's trailing Enter) looks the piece up straight away. */
  protected onSerialKey(event: KeyboardEvent, index: number): void {
    if (event.key === 'Enter' || event.key === 'Return' || event.keyCode === 13) {
      event.preventDefault();
      this.lookup(index);
    }
  }

  protected lookup(index: number): void {
    const row = this.rows()[index];
    const serial = row.serial.trim();
    if (!serial || row.looking || (row.lookup && row.lookup.serialNumber === serial.padStart(3, '0'))) {
      return;
    }
    const canonical = serial.padStart(3, '0');
    const duplicate = this.rows().some((other, i) => i !== index && other.lookup?.serialNumber === canonical);
    if (duplicate) {
      this.patch(index, { lookupError: `Serial ${canonical} is already on this invoice.` });
      return;
    }

    this.patch(index, { looking: true, lookupError: null });
    this.sales.lookupItem(serial, quiet()).subscribe({
      next: (item) => {
        const key = row.key;
        this.rows.update((rows) =>
          rows.map((r) =>
            r.key === key
              ? { ...r, looking: false, serial: item.serialNumber, lookup: item, particulars: r.particulars || item.suggestedParticulars }
              : r,
          ),
        );
        if (this.entryMode() === 'MANUAL' && this.rows().at(-1)?.key === key) {
          this.addRow(false);
        }
        this.schedule();
      },
      error: (error) => {
        const key = row.key;
        this.rows.update((rows) =>
          rows.map((r) => (r.key === key ? { ...r, looking: false, lookup: null, lookupError: errorMessage(error) } : r)),
        );
      },
    });
  }

  // --- old gold -------------------------------------------------------------

  protected toggleAdjustment(option: OldMetalOption): void {
    const current = { ...this.adjustments() };
    if (current[option.id] !== undefined) {
      delete current[option.id];
    } else {
      const grand = this.calculation()?.totals.grandTotal ?? option.availableAmount;
      const alreadyUsed = Object.values(current).reduce((sum, value) => sum + (toNumber(value) ?? 0), 0);
      const room = Math.max(0, grand - alreadyUsed);
      current[option.id] = String(Math.min(option.availableAmount, room || option.availableAmount));
    }
    this.adjustments.set(current);
    this.schedule();
  }

  protected setAdjustment(id: number, value: string): void {
    this.adjustments.update((current) => ({ ...current, [id]: value }));
    this.schedule();
  }

  // --- payments -------------------------------------------------------------

  protected addPayment(): void {
    this.payments.update((rows) => [...rows, { key: this.nextKey++, method: 'CASH', amount: '', reference: '' }]);
  }

  protected payInFull(): void {
    const totals = this.calculation()?.totals;
    if (!totals) {
      return;
    }
    const others = this.payments().slice(1).reduce((sum, p) => sum + (toNumber(p.amount) ?? 0), 0);
    const remaining = Math.max(0, totals.netPayable - others);
    if (this.payments().length === 0) {
      this.payments.set([{ key: this.nextKey++, method: 'CASH', amount: String(remaining), reference: '' }]);
    } else {
      this.patchPayment(0, { amount: String(remaining) });
    }
    this.schedule();
  }

  protected patchPayment(index: number, changes: Partial<PaymentRow>): void {
    this.payments.update((rows) => rows.map((row, i) => (i === index ? { ...row, ...changes } : row)));
    this.schedule();
  }

  protected removePayment(index: number): void {
    this.payments.update((rows) => rows.filter((_, i) => i !== index));
    this.schedule();
  }

  // --- save -----------------------------------------------------------------

  protected save(): void {
    if (!this.canSave()) {
      return;
    }
    this.saving.set(true);
    this.saveError.set(null);
    this.sales.create(this.buildRequest(), quiet()).subscribe({
      next: (sale) => {
        this.saving.set(false);
        this.notifications.success(`Invoice ${sale.invoiceNumber} saved.`);
        void this.router.navigate(['/sales', sale.id], { queryParams: { print: 1 } });
      },
      error: (error) => {
        this.saving.set(false);
        this.saveError.set(errorMessage(error));
        // Another counter may have just sold a piece - re-check everything.
        this.schedule();
      },
    });
  }

  private buildRequest(): SaleRequest {
    return {
      customerId: this.customer()?.id ?? null,
      invoiceDate: this.invoiceDate() || null,
      items: this.readyRows().map((row) => ({
        serialNumber: row.lookup!.serialNumber,
        particulars: row.particulars.trim() || null,
        // Sent only for a box. A single piece is sold whole and the server
        // refuses a weight for it rather than quietly ignoring one.
        weightGrams: row.lookup!.bulk ? toNumber(row.weight) : null,
        wastagePercentage: toNumber(row.wastage),
        ratePerGram: toNumber(row.rate),
        makingCharge: toNumber(row.making),
      })),
      discountAmount: toNumber(this.discount()),
      oldMetalAdjustments: Object.entries(this.adjustments())
        .map(([id, amount]) => ({ transactionId: Number(id), amount: toNumber(amount) }))
        .filter((adjustment) => (adjustment.amount ?? 0) > 0),
      payments: this.payments()
        .filter((payment) => (toNumber(payment.amount) ?? 0) > 0)
        .map((payment) => ({
          method: payment.method,
          amount: toNumber(payment.amount),
          referenceNumber: payment.reference.trim() || null,
        })),
      remarks: this.remarks().trim() || null,
    };
  }

  private emptyRow(): Row {
    return {
      key: this.nextKey++,
      serial: '',
      lookup: null,
      lookupError: null,
      looking: false,
      particulars: '',
      weight: '',
      wastage: '',
      rate: '',
      making: '',
    };
  }
}
