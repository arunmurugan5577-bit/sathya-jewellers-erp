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
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Subject, catchError, debounceTime, of, switchMap } from 'rxjs';

import { SUPPRESS_ERROR_TOAST } from '../../core/interceptors/error.interceptor';
import { NotificationService } from '../../core/services/notification.service';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { isApiError } from '../../shared/models/api-error.model';
import { CustomerSummary } from '../../shared/models/sales.model';
import {
  WholesaleCalculation,
  WholesaleItemLookup,
  WholesaleLine,
  WholesaleRequest,
} from '../../shared/models/wholesale.model';
import { InrPipe } from '../../shared/pipes/inr.pipe';
import { WeightPipe } from '../../shared/pipes/weight.pipe';
import { CustomerPickerComponent } from '../customers/customer-picker.component';
import { WholesaleApiService } from './wholesale-api.service';

/** One piece on the estimate, as typed at the counter. */
interface Row {
  key: number;
  serial: string;
  lookup: WholesaleItemLookup | null;
  lookupError: string | null;
  looking: boolean;
  jewelName: string;
  touch: string;
  rate: string;
  making: string;
  stone: string;
}

const quiet = () => new HttpContext().set(SUPPRESS_ERROR_TOAST, true);

function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse && isApiError(error.error)) {
    const fields = Object.values(error.error.fieldErrors ?? {});
    return fields.length ? fields.join(' ') : error.error.message;
  }
  return 'Something went wrong. Try again.';
}

function toNumber(value: string): number | null {
  const n = Number(value);
  return value.trim() !== '' && Number.isFinite(n) ? n : null;
}

@Component({
  selector: 'app-wholesale-editor',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, PageHeaderComponent, CustomerPickerComponent, InrPipe, WeightPipe],
  template: `
    <div class="page">
      <app-page-header
        title="Wholesale estimate"
        subtitle="Priced in pure gold: jewel weight x touch, at the day's pure rate.">
        <a class="btn btn--ghost" routerLink="/wholesale">All estimates</a>
      </app-page-header>

      <div class="layout">
        <div>
          <!-- Party and rate -->
          <section class="card">
            <div class="card__header"><h2 class="card__title">Party</h2></div>
            <div class="card__body grid-2">
              <div class="field field--full-mobile">
                <app-customer-picker [(customer)]="customer" />
              </div>
              <div class="field">
                <label class="field__label field__label--required" for="pureRate">Pure rate / g</label>
                <input id="pureRate" class="input numeric" type="number" min="0" step="0.01"
                       [value]="pureRate()" (input)="setPureRate($any($event.target).value)" />
                <p class="field__hint">One gram of pure gold today. The whole estimate is valued from it.</p>
              </div>
              <div class="field">
                <label class="field__label" for="estimateDate">Date</label>
                <input id="estimateDate" class="input" type="date" [max]="today" [value]="estimateDate()"
                       (change)="estimateDate.set($any($event.target).value); schedule()" />
              </div>
            </div>
            @if (customer(); as party) {
              @if (calculation(); as calc) {
                <div class="opening">
                  Opening balance
                  <strong>{{ calc.totals.openingPureGrams | weight }} g</strong> pure
                  @if (calc.totals.openingMiscAmount) {
                    &nbsp;+&nbsp;<strong>{{ calc.totals.openingMiscAmount | inr: 'whole' }}</strong>
                  }
                  &nbsp;&middot;&nbsp;{{ calc.totals.openingValue | inr: 'whole' }}
                </div>
              }
            }
          </section>

          <!-- Pieces -->
          <section class="card">
            <div class="card__header">
              <h2 class="card__title">Pieces</h2>
            </div>

            <div class="scan" [class.scan--error]="scanError()">
              <label class="field__label" for="wsScan">Scan a tag</label>
              <input #scanBox id="wsScan" class="input scan__input" inputmode="numeric" maxlength="6"
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
                  Scan each piece, then set the touch on every line.
                }
              </p>
            </div>

            <div class="lines">
              @if (!rows().length) {
                <p class="lines__empty">No pieces yet. Scan the tag on the first one.</p>
              }
              @for (row of rows(); track row.key; let i = $index) {
                <div class="line" [class.line--error]="row.lookupError">
                  <div class="line__head">
                    <span class="line__no">{{ i + 1 }}</span>
                    <span class="line__serial">{{ row.serial }}</span>
                    <input class="input line__name" maxlength="200" placeholder="Jewel name"
                           [value]="row.jewelName" (input)="patch(i, { jewelName: $any($event.target).value })" />
                    <button type="button" class="btn btn--ghost btn--icon" aria-label="Remove piece"
                            (click)="removeRow(i)">&times;</button>
                  </div>
                  @if (row.lookupError) {
                    <p class="field__error">{{ row.lookupError }}</p>
                  }
                  @if (row.lookup; as piece) {
                    <div class="inputs">
                      <div class="figure-cell">
                        <span class="field__label">Jewel wt</span>{{ piece.jewelWeightGrams | weight }}
                      </div>
                      <div class="field">
                        <label class="field__label field__label--required">Touch %</label>
                        <input class="input numeric" type="number" min="0" max="100" step="0.01"
                               [value]="row.touch" (input)="patch(i, { touch: $any($event.target).value })" />
                      </div>
                      <div class="field">
                        <label class="field__label">Rate / g</label>
                        <input class="input numeric" type="number" min="0" step="0.01"
                               [placeholder]="pureRate()" [value]="row.rate"
                               (input)="patch(i, { rate: $any($event.target).value })" />
                      </div>
                      <div class="field">
                        <label class="field__label">Making</label>
                        <input class="input numeric" type="number" min="0" step="0.01"
                               [value]="row.making" (input)="patch(i, { making: $any($event.target).value })" />
                      </div>
                      <div class="field">
                        <label class="field__label">Stone</label>
                        <input class="input numeric" type="number" min="0" step="0.01"
                               [value]="row.stone" (input)="patch(i, { stone: $any($event.target).value })" />
                      </div>
                      @if (priced(row.serial); as line) {
                        <div class="figure-cell"><span class="field__label">Pure wt</span>{{ line.pureWeightGrams | weight }}</div>
                        <div class="figure-cell figure-cell--amount">
                          <span class="field__label">Amount</span>{{ line.itemAmount | inr: 'whole' }}
                        </div>
                      }
                    </div>
                  }
                </div>
              }
            </div>
          </section>

          <section class="card">
            <div class="card__header"><h2 class="card__title">Remarks</h2></div>
            <div class="card__body">
              <textarea class="textarea" maxlength="500" [value]="remarks()"
                        (input)="remarks.set($any($event.target).value)"></textarea>
            </div>
          </section>
        </div>

        <!-- Totals -->
        <aside class="totals">
          <section class="card">
            <div class="card__header"><h2 class="card__title">Totals</h2></div>
            <div class="card__body">
              @if (calcError()) {
                <p class="field__error">{{ calcError() }}</p>
              }
              @if (calculation(); as calc) {
                <dl class="sums">
                  <dt>Total pure weight</dt><dd>{{ calc.totals.totalPureGrams | weight }} g</dd>
                  <dt>Making + stone</dt><dd>{{ calc.totals.totalMiscAmount | inr: 'whole' }}</dd>
                  <dt class="sums__grand">Estimate total</dt>
                  <dd class="sums__grand">{{ calc.totals.totalAmount | inr: 'whole' }}</dd>
                  <dd class="words">{{ calc.totals.totalAmountInWords }}</dd>

                  <dt class="sums__section">Closing pure</dt>
                  <dd class="sums__section">{{ calc.totals.closingPureGrams | weight }} g</dd>
                  <dt>Closing misc</dt><dd>{{ calc.totals.closingMiscAmount | inr: 'whole' }}</dd>
                  <dt class="sums__grand">Closing value</dt>
                  <dd class="sums__grand">{{ calc.totals.closingValue | inr: 'whole' }}</dd>
                </dl>
              } @else {
                <p class="text-muted">Scan a piece and set its touch to see the figures.</p>
              }

              @if (saveError()) {
                <p class="field__error">{{ saveError() }}</p>
              }
              <button type="button" class="btn btn--primary btn--block" [disabled]="!canSave()" (click)="save()">
                {{ saving() ? 'Saving...' : 'Save estimate' }}
              </button>
              @if (!customer()) {
                <p class="field__hint">Choose a party to save.</p>
              }
            </div>
          </section>
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
      .grid-2 { display: grid; grid-template-columns: minmax(0, 2fr) minmax(10rem, 1fr) minmax(10rem, 1fr); gap: var(--space-4); }
      @media (max-width: 760px) { .grid-2 { grid-template-columns: 1fr; } }
      .opening { padding: var(--space-3) var(--space-5); border-top: 1px solid var(--border-subtle); color: var(--text-secondary); font-size: var(--text-sm); }
      .scan { padding: var(--space-4) var(--space-5); border-top: 1px solid var(--border-subtle); }
      .scan--error .scan__input { border-color: var(--danger); }
      .scan__input { max-width: 16rem; font-size: var(--text-lg); font-variant-numeric: tabular-nums; letter-spacing: 0.08em; }
      .scan__hint { margin: var(--space-2) 0 0; font-size: var(--text-sm); color: var(--text-muted); min-height: 1.2em; }
      .lines { display: flex; flex-direction: column; }
      .lines__empty { margin: 0; padding: var(--space-5); color: var(--text-muted); border-top: 1px solid var(--border-subtle); }
      .line { padding: var(--space-4) var(--space-5); border-top: 1px solid var(--border-subtle); display: flex; flex-direction: column; gap: var(--space-3); }
      .line--error { background: color-mix(in srgb, var(--danger) 6%, transparent); }
      .line__head { display: flex; align-items: center; gap: var(--space-3); }
      .line__no { color: var(--text-muted); font-variant-numeric: tabular-nums; min-width: 1.5rem; }
      .line__serial { font-weight: 700; font-variant-numeric: tabular-nums; }
      .line__name { flex: 1; }
      .inputs { display: grid; grid-template-columns: repeat(auto-fit, minmax(7rem, 1fr)); gap: var(--space-3); align-items: end; }
      .figure-cell { display: flex; flex-direction: column; font-variant-numeric: tabular-nums; padding-bottom: var(--space-2); }
      .figure-cell--amount { font-weight: 700; font-size: var(--text-md); }
      .sums { display: grid; grid-template-columns: 1fr auto; gap: var(--space-2) var(--space-3); margin: 0 0 var(--space-4); align-items: center; }
      .sums dt { color: var(--text-secondary); }
      .sums dd { margin: 0; text-align: right; font-variant-numeric: tabular-nums; }
      .sums__section { border-top: 1px solid var(--border-subtle); padding-top: var(--space-2); }
      .sums__grand { font-weight: 700; color: var(--text-primary) !important; }
      .words { grid-column: 1 / -1; font-size: var(--text-sm); font-style: italic; color: var(--text-muted); text-align: right; }
      .text-danger { color: var(--danger); font-weight: 600; }
    `,
  ],
})
export class WholesaleEditorComponent {
  private readonly api = inject(WholesaleApiService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly scanBox = viewChild<ElementRef<HTMLInputElement>>('scanBox');
  private readonly recalc$ = new Subject<void>();
  private nextKey = 1;
  private scanIdle?: ReturnType<typeof setTimeout>;

  protected readonly today = new Date().toLocaleDateString('en-CA');

  protected readonly customer = signal<CustomerSummary | null>(null);
  protected readonly estimateDate = signal(this.today);
  protected readonly pureRate = signal('');
  protected readonly remarks = signal('');
  protected readonly rows = signal<Row[]>([]);

  protected readonly calculation = signal<WholesaleCalculation | null>(null);
  protected readonly calculating = signal(false);
  protected readonly calcError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);

  protected readonly scanValue = signal('');
  protected readonly scanStatus = signal<string | null>(null);
  protected readonly scanError = signal<string | null>(null);
  protected readonly scanning = signal(false);

  private readonly pricedBySerial = computed(() => {
    const map = new Map<string, WholesaleLine>();
    for (const line of this.calculation()?.lines ?? []) {
      map.set(line.serialNumber, line);
    }
    return map;
  });

  private readonly readyRows = computed(() =>
    this.rows().filter((row) => row.lookup && (toNumber(row.touch) ?? 0) > 0),
  );

  protected readonly canSave = computed(
    () =>
      !this.saving() &&
      !this.calculating() &&
      !!this.customer() &&
      (toNumber(this.pureRate()) ?? 0) > 0 &&
      !!this.calculation() &&
      !this.calcError() &&
      this.readyRows().length > 0 &&
      this.readyRows().length === this.rows().length,
  );

  constructor() {
    this.recalc$
      .pipe(
        debounceTime(350),
        switchMap(() => {
          const request = this.buildRequest();
          if (request.items.length === 0 || !request.pureRatePerGram) {
            this.calculating.set(false);
            return of(null);
          }
          this.calculating.set(true);
          return this.api.calculate(request, quiet()).pipe(
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

    // A different party means a different opening balance.
    effect(() => {
      this.customer();
      this.schedule();
    });

    this.focusScanBox();
  }

  protected priced(serial: string): WholesaleLine | undefined {
    return this.pricedBySerial().get(serial);
  }

  protected schedule(): void {
    this.saveError.set(null);
    this.recalc$.next();
  }

  protected setPureRate(value: string): void {
    this.pureRate.set(value);
    this.schedule();
  }

  protected patch(index: number, changes: Partial<Row>): void {
    this.rows.update((rows) => rows.map((row, i) => (i === index ? { ...row, ...changes } : row)));
    this.schedule();
  }

  protected removeRow(index: number): void {
    this.rows.update((rows) => rows.filter((_, i) => i !== index));
    this.focusScanBox();
    this.schedule();
  }

  // --- scanning -------------------------------------------------------------

  protected onScanKey(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === 'Return' || event.keyCode === 13) {
      event.preventDefault();
      this.commitScan();
    }
  }

  protected onScanInput(value: string): void {
    const digits = value.replace(/\D/g, '').slice(0, 6);
    this.scanValue.set(digits);
    this.scanError.set(null);
    clearTimeout(this.scanIdle);
    // Not every scanner sends an Enter; one that does not simply stops typing.
    if (digits.length >= 4) {
      this.scanIdle = setTimeout(() => this.commitScan(), 200);
    }
  }

  /** Keeps a scan working when the cursor is in one of the touch boxes. */
  protected onAnywhereKey(event: KeyboardEvent): void {
    if (event.ctrlKey || event.altKey || event.metaKey) {
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
    const canonical = serial.padStart(6, '0');
    if (this.rows().some((row) => row.lookup?.serialNumber === canonical)) {
      this.scanValue.set('');
      this.scanStatus.set(null);
      this.scanError.set(`Serial ${canonical} is already on this estimate.`);
      this.focusScanBox();
      return;
    }

    this.scanning.set(true);
    this.scanStatus.set(null);
    this.api.lookupItem(serial, quiet()).subscribe({
      next: (piece) => {
        const last = this.rows().at(-1);
        this.rows.update((rows) => [
          ...rows,
          {
            key: this.nextKey++,
            serial: piece.serialNumber,
            lookup: piece,
            lookupError: null,
            looking: false,
            jewelName: piece.jewelName ?? '',
            // The touch is usually the same across a lot, so carry it over.
            touch: last?.touch ?? (piece.suggestedPurePercentage?.toString() ?? ''),
            rate: last?.rate ?? '',
            making: '',
            stone: '',
          },
        ]);
        this.scanning.set(false);
        this.scanValue.set('');
        this.scanStatus.set(`Added ${piece.serialNumber} - ${piece.jewelName}`);
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

  // --- saving ---------------------------------------------------------------

  protected save(): void {
    if (!this.canSave()) {
      return;
    }
    this.saving.set(true);
    this.saveError.set(null);
    this.api.create(this.buildRequest(), quiet()).subscribe({
      next: (estimate) => {
        this.saving.set(false);
        this.notifications.success(`Estimate ${estimate.estimateNumber} saved.`);
        void this.router.navigate(['/wholesale', estimate.id], { queryParams: { print: 1 } });
      },
      error: (error) => {
        this.saving.set(false);
        this.saveError.set(errorMessage(error));
        // Another counter may have just sold a piece - re-check everything.
        this.schedule();
      },
    });
  }

  private buildRequest(): WholesaleRequest {
    return {
      customerId: this.customer()?.id ?? null,
      estimateDate: this.estimateDate() || null,
      pureRatePerGram: toNumber(this.pureRate()),
      items: this.readyRows().map((row) => ({
        serialNumber: row.lookup!.serialNumber,
        jewelName: row.jewelName.trim() || null,
        purePercentage: toNumber(row.touch),
        ratePerGram: toNumber(row.rate),
        makingCharge: toNumber(row.making),
        stoneAmount: toNumber(row.stone),
      })),
      remarks: this.remarks().trim() || null,
    };
  }
}
