import { HttpContext, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { SUPPRESS_ERROR_TOAST } from '../../core/interceptors/error.interceptor';
import { NotificationService } from '../../core/services/notification.service';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { isApiError } from '../../shared/models/api-error.model';
import { Lookup } from '../../shared/models/lookup.model';
import { CustomerSummary, OldMetalRequest } from '../../shared/models/sales.model';
import { InrPipe } from '../../shared/pipes/inr.pipe';
import { ItemTypeService } from '../masters/item-type/item-type.service';
import { PurityService } from '../masters/purity/purity.service';
import { CustomerPickerComponent } from '../customers/customer-picker.component';
import { OldMetalApiService } from '../sales/sales-api.service';

interface Line {
  key: number;
  itemTypeId: number | null;
  purityId: number | null;
  purities: Lookup[];
  particulars: string;
  hsnCode: string;
  net: string;
  gross: string;
  rate: string;
}

/** Old metal the shop accepts. Mirrors app.old-metal.item-type-codes on the server, which has the final say. */
const OLD_METAL_CODES = ['GOLD', 'SILV'];
const DEFAULT_HSN: Record<string, string> = { GOLD: '7108', SILV: '7106' };

@Component({
  selector: 'app-old-metal-form',
  standalone: true,
  imports: [RouterLink, PageHeaderComponent, CustomerPickerComponent, InrPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-header title="Old gold / silver purchase" subtitle="Amount = net weight x rate, billed in whole rupees.">
        <a class="btn btn--ghost" routerLink="/old-metal">All purchase bills</a>
      </app-page-header>

      <section class="card">
        <div class="card__header"><h2 class="card__title">Customer</h2></div>
        <div class="card__body grid-2">
          <app-customer-picker [(customer)]="customer" />
          <div class="field">
            <label class="field__label" for="date">Date</label>
            <input id="date" class="input" type="date" [max]="today" [value]="date()" (change)="date.set($any($event.target).value)" />
          </div>
        </div>
      </section>

      <section class="card">
        <div class="card__header">
          <h2 class="card__title">Particulars</h2>
          <button type="button" class="btn btn--sm" (click)="addLine()">+ Add line</button>
        </div>
        @for (line of lines(); track line.key; let i = $index) {
          <div class="line">
            <div class="field">
              <label class="field__label field__label--required">Metal</label>
              <select class="select" (change)="setItemType(i, $any($event.target).value)">
                <option value="">Select</option>
                @for (type of itemTypes(); track type.id) {
                  <option [value]="type.id" [selected]="type.id === line.itemTypeId">{{ type.name }}</option>
                }
              </select>
            </div>
            <div class="field">
              <label class="field__label">Purity</label>
              <select class="select" [disabled]="!line.itemTypeId" (change)="patch(i, { purityId: $any($event.target).value ? +$any($event.target).value : null })">
                <option value="">-</option>
                @for (purity of line.purities; track purity.id) {
                  <option [value]="purity.id" [selected]="purity.id === line.purityId">{{ purity.name }}</option>
                }
              </select>
            </div>
            <div class="field field--wide">
              <label class="field__label field__label--required">Particulars</label>
              <input class="input" maxlength="200" placeholder="e.g. Gold coin" [value]="line.particulars" (input)="patch(i, { particulars: $any($event.target).value })" />
            </div>
            <div class="field">
              <label class="field__label field__label--required">HSN</label>
              <input class="input" maxlength="8" inputmode="numeric" [value]="line.hsnCode" (input)="patch(i, { hsnCode: $any($event.target).value })" />
            </div>
            <div class="field">
              <label class="field__label field__label--required">Net wt (g)</label>
              <input class="input numeric" type="number" min="0" step="0.001" [value]="line.net" (input)="patch(i, { net: $any($event.target).value })" />
            </div>
            <div class="field">
              <label class="field__label">Gross wt (g)</label>
              <input class="input numeric" type="number" min="0" step="0.001" [value]="line.gross" (input)="patch(i, { gross: $any($event.target).value })" />
            </div>
            <div class="field">
              <label class="field__label field__label--required">Rate / g</label>
              <input class="input numeric" type="number" min="0" step="0.01" [value]="line.rate" (input)="patch(i, { rate: $any($event.target).value })" />
            </div>
            <div class="field estimate">
              <span class="field__label">Amount (est.)</span>
              <span>{{ estimate(line) | inr: 'whole' }}</span>
            </div>
            <button type="button" class="btn btn--ghost btn--icon" aria-label="Remove line" (click)="removeLine(i)">&times;</button>
          </div>
        }
        <div class="card__body total">
          <span>Estimated total <strong>{{ estimatedTotal() | inr: 'whole' }}</strong></span>
          <span class="text-muted">The saved bill shows the server's figures.</span>
        </div>
      </section>

      <section class="card">
        <div class="card__body field">
          <label class="field__label" for="remarks">Remarks</label>
          <textarea id="remarks" class="textarea" rows="2" maxlength="500" (input)="remarks.set($any($event.target).value)"></textarea>
          @if (error()) { <div class="alert alert--error">{{ error() }}</div> }
          <div class="row row--end">
            <button type="button" class="btn btn--primary" [disabled]="saving() || !customer()" (click)="save()">
              {{ saving() ? 'Saving...' : 'Save & print bill' }}
            </button>
          </div>
        </div>
      </section>
    </div>
  `,
  styles: [
    `
      .grid-2 { display: grid; grid-template-columns: minmax(0, 2fr) minmax(10rem, 1fr); gap: var(--space-4); }
      @media (max-width: 640px) { .grid-2 { grid-template-columns: 1fr; } }
      .line {
        display: grid; grid-template-columns: 8rem 8rem minmax(10rem, 1fr) 6rem 7rem 7rem 7rem 7rem auto;
        gap: var(--space-3); align-items: end; padding: var(--space-4) var(--space-5); border-top: 1px solid var(--border-subtle);
      }
      @media (max-width: 1200px) { .line { grid-template-columns: repeat(auto-fit, minmax(8rem, 1fr)); } }
      .estimate { font-variant-numeric: tabular-nums; font-weight: 600; padding-bottom: var(--space-2); }
      .total { display: flex; justify-content: space-between; flex-wrap: wrap; gap: var(--space-2); border-top: 1px solid var(--border-subtle); }
    `,
  ],
})
export class OldMetalFormComponent {
  private readonly api = inject(OldMetalApiService);
  private readonly itemTypeService = inject(ItemTypeService);
  private readonly purityService = inject(PurityService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  private nextKey = 1;

  protected readonly today = new Date().toLocaleDateString('en-CA');
  protected readonly customer = signal<CustomerSummary | null>(null);
  protected readonly date = signal(this.today);
  protected readonly remarks = signal('');
  protected readonly itemTypes = signal<Lookup[]>([]);
  protected readonly lines = signal<Line[]>([this.emptyLine()]);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly estimatedTotal = computed(() => this.lines().reduce((sum, line) => sum + this.estimate(line), 0));

  constructor() {
    this.itemTypeService.lookup().subscribe((types) =>
      this.itemTypes.set(types.filter((type) => OLD_METAL_CODES.includes((type.code ?? '').toUpperCase()))),
    );
  }

  protected estimate(line: Line): number {
    const net = Number(line.net);
    const rate = Number(line.rate);
    return net > 0 && rate > 0 ? Math.round(net * rate) : 0;
  }

  protected addLine(): void {
    const last = this.lines().at(-1);
    this.lines.update((lines) => [...lines, { ...this.emptyLine(), rate: last?.rate ?? '' }]);
  }

  protected removeLine(index: number): void {
    this.lines.update((lines) => (lines.length > 1 ? lines.filter((_, i) => i !== index) : [this.emptyLine()]));
  }

  protected patch(index: number, changes: Partial<Line>): void {
    this.lines.update((lines) => lines.map((line, i) => (i === index ? { ...line, ...changes } : line)));
  }

  protected setItemType(index: number, value: string): void {
    const id = value ? Number(value) : null;
    const type = this.itemTypes().find((t) => t.id === id);
    const hsn = DEFAULT_HSN[(type?.code ?? '').toUpperCase()] ?? '';
    const key = this.lines()[index].key;
    this.patch(index, { itemTypeId: id, purityId: null, purities: [], hsnCode: this.lines()[index].hsnCode || hsn });
    if (id) {
      this.purityService.lookupByItemType(id).subscribe((purities) =>
        this.lines.update((lines) => lines.map((line) => (line.key === key ? { ...line, purities } : line))),
      );
    }
  }

  protected save(): void {
    const request: OldMetalRequest = {
      customerId: this.customer()?.id ?? null,
      transactionDate: this.date() || null,
      remarks: this.remarks().trim() || null,
      items: this.lines().map((line) => ({
        itemTypeId: line.itemTypeId,
        purityId: line.purityId,
        particulars: line.particulars.trim(),
        hsnCode: line.hsnCode.trim(),
        netWeightGrams: line.net ? Number(line.net) : null,
        grossWeightGrams: line.gross ? Number(line.gross) : null,
        ratePerGram: line.rate ? Number(line.rate) : null,
      })),
    };
    this.saving.set(true);
    this.error.set(null);
    this.api.create(request, new HttpContext().set(SUPPRESS_ERROR_TOAST, true)).subscribe({
      next: (bill) => {
        this.saving.set(false);
        this.notifications.success(`Purchase bill ${bill.transactionNumber} saved.`);
        void this.router.navigate(['/old-metal', bill.id], { queryParams: { print: 1 } });
      },
      error: (error: unknown) => {
        this.saving.set(false);
        if (error instanceof HttpErrorResponse && isApiError(error.error)) {
          const fields = Object.values(error.error.fieldErrors ?? {});
          this.error.set(fields.length ? [...new Set(fields)].join(' ') : error.error.message);
        } else {
          this.error.set('Could not save the purchase bill. Try again.');
        }
      },
    });
  }

  private emptyLine(): Line {
    return { key: this.nextKey++, itemTypeId: null, purityId: null, purities: [], particulars: '', hsnCode: '', net: '', gross: '', rate: '' };
  }
}
