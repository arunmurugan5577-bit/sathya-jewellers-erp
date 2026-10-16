import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { NotificationService } from '../../core/services/notification.service';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { SpinnerComponent } from '../../shared/components/spinner.component';
import { isApiError } from '../../shared/models/api-error.model';
import { ChitScheme } from '../../shared/models/chit.model';
import { CustomerSummary } from '../../shared/models/sales.model';
import { InrPipe } from '../../shared/pipes/inr.pipe';
import { CustomerPickerComponent } from '../customers/customer-picker.component';
import { ChitApiService } from './chit-api.service';

/**
 * Starting a chit.
 *
 * <p>The schemes are shown as cards rather than a dropdown because what
 * separates them is what the customer ends up with - 24,000 in hand, or
 * however much gold 36,000 buys over the year - and that does not fit in a
 * list of six amounts.
 */
@Component({
  selector: 'app-chit-open',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, PageHeaderComponent, SpinnerComponent, CustomerPickerComponent, InrPipe],
  template: `
    <div class="page">
      <app-page-header title="Start a chit" subtitle="Eleven months from the customer, the twelfth from the shop.">
        <a class="btn btn--ghost" routerLink="/chits">All chits</a>
      </app-page-header>

      @if (loading()) {
        <app-spinner />
      } @else {
        <section class="card">
          <div class="card__body">
            <h2 class="card__title">Customer</h2>
            <app-customer-picker [(customer)]="customer" />
          </div>
        </section>

        <section class="card">
          <div class="card__body">
            <h2 class="card__title">Scheme</h2>
            <p class="text-muted">
              A money chit saves rupees. A gold chit buys gold every month at that day's rate, so
              what it comes to depends on how the rate moves.
            </p>

            <div class="schemes">
              @for (scheme of schemes(); track scheme.id) {
                <button
                  type="button"
                  class="scheme"
                  [class.scheme--on]="scheme.id === schemeId()"
                  (click)="schemeId.set(scheme.id)"
                >
                  <span class="scheme__amount">{{ scheme.monthlyAmount | inr: 'whole' }}</span>
                  <span class="scheme__per">a month</span>
                  <span class="badge" [class.badge--gold-tone]="scheme.schemeType === 'GOLD'">
                    {{ scheme.schemeType === 'GOLD' ? 'Gold' : 'Money' }}
                  </span>
                  <span class="scheme__end">
                    @if (scheme.schemeType === 'GOLD') {
                      Gold worth {{ scheme.maturityAmount | inr: 'whole' }}, bought month by month
                    } @else {
                      {{ scheme.maturityAmount | inr: 'whole' }} at the end
                    }
                  </span>
                  <span class="scheme__terms">
                    Pays {{ scheme.payableInstalments }}, gets {{ scheme.totalInstalments }}
                  </span>
                </button>
              }
            </div>
          </div>
        </section>

        <section class="card">
          <div class="card__body grid">
            <div class="field">
              <label class="field__label" for="startedOn">First month</label>
              <input
                id="startedOn"
                class="input"
                type="date"
                [value]="startedOn()"
                [max]="today"
                (change)="startedOn.set($any($event.target).value)"
              />
              <p class="field__hint">Defaults to today. It cannot be in the future.</p>
            </div>
            <div class="field">
              <label class="field__label" for="remarks">Remarks</label>
              <input
                id="remarks"
                class="input"
                maxlength="500"
                [value]="remarks()"
                (input)="remarks.set($any($event.target).value)"
              />
            </div>
          </div>

          @if (error()) {
            <div class="card__body"><div class="alert alert--error">{{ error() }}</div></div>
          }

          <div class="card__body actions">
            @if (!ready()) {
              <span class="text-muted">Choose a customer and a scheme to start.</span>
            }
            <button type="button" class="btn btn--primary" [disabled]="!ready() || busy()" (click)="save()">
              {{ busy() ? 'Starting...' : 'Start chit' }}
            </button>
          </div>
        </section>
      }
    </div>
  `,
  styles: [
    `
      .page > .card { margin-bottom: var(--space-4); }
      .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr)); gap: var(--space-4); }
      .schemes {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr));
        gap: var(--space-3);
        margin-top: var(--space-3);
      }
      .scheme {
        display: grid;
        justify-items: start;
        gap: 2px;
        padding: var(--space-4);
        text-align: left;
        cursor: pointer;
        background: var(--surface-card);
        border: 1px solid var(--border-subtle);
        border-radius: var(--radius-md);
      }
      .scheme:hover { border-color: var(--brand); }
      .scheme--on { border-color: var(--brand); box-shadow: inset 0 0 0 1px var(--brand); }
      .scheme__amount { font-size: var(--text-xl); font-weight: 700; }
      .scheme__per { color: var(--text-muted); font-size: var(--text-xs); }
      .scheme__end { margin-top: var(--space-2); color: var(--text-secondary); }
      .scheme__terms { color: var(--text-muted); font-size: var(--text-xs); }
      .badge--gold-tone { color: #8a6d1f; background: var(--gold-soft); }
      .actions { display: flex; justify-content: flex-end; align-items: center; gap: var(--space-3); }
    `,
  ],
})
export class ChitOpenComponent implements OnInit {
  private readonly api = inject(ChitApiService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  protected readonly today = new Date().toISOString().slice(0, 10);

  protected readonly schemes = signal<ChitScheme[]>([]);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly customer = signal<CustomerSummary | null>(null);
  protected readonly schemeId = signal<number | null>(null);
  protected readonly startedOn = signal(this.today);
  protected readonly remarks = signal('');

  protected readonly ready = computed(() => !!this.customer() && !!this.schemeId());

  ngOnInit(): void {
    this.api.schemes().subscribe({
      next: (schemes) => {
        this.schemes.set(schemes);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected save(): void {
    const customer = this.customer();
    const schemeId = this.schemeId();
    if (!customer || !schemeId) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api
      .open({
        customerId: customer.id,
        schemeId,
        startedOn: this.startedOn() || null,
        remarks: this.remarks().trim() || null,
      })
      .subscribe({
        next: (chit) => {
          this.busy.set(false);
          this.notifications.success(`Chit ${chit.chitNumber} started for ${chit.customerName}.`);
          void this.router.navigate(['/chits', chit.id]);
        },
        error: (err: unknown) => {
          this.busy.set(false);
          this.error.set(
            err instanceof HttpErrorResponse && isApiError(err.error)
              ? err.error.message
              : 'Could not start the chit. Try again.',
          );
        },
      });
  }
}
