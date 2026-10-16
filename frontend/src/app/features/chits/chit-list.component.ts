import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';

import { Permissions } from '../../core/auth/permissions';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import {
  CHIT_STATUS_LABELS,
  ChitSchemeType,
  ChitStatus,
  ChitSummary,
} from '../../shared/models/chit.model';
import { Page } from '../../shared/models/page.model';
import { InrPipe } from '../../shared/pipes/inr.pipe';
import { WeightPipe } from '../../shared/pipes/weight.pipe';
import { ChitApiService } from './chit-api.service';
import { ChitStatusBadgeComponent } from './chit-status-badge.component';

/**
 * Every chit the shop is running.
 *
 * <p>The two kinds are shown side by side but counted differently: a money
 * chit's balance is rupees, a gold chit's is grams. Mixing them into one
 * "balance" column would read as a total that means nothing, so each row shows
 * the one that applies to it.
 */
@Component({
  selector: 'app-chit-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, PageHeaderComponent, HasPermissionDirective, ChitStatusBadgeComponent, InrPipe, WeightPipe],
  template: `
    <div class="page">
      <app-page-header
        title="Chits"
        subtitle="Eleven months from the customer, the twelfth from the shop."
      >
        <a class="btn btn--primary" routerLink="/chits/new" *appHasPermission="permissions.CHIT_CREATE">
          + Start a chit
        </a>
      </app-page-header>

      <section class="card">
        <div class="card__body filters">
          <div class="field">
            <label class="field__label" for="search">Search</label>
            <input
              id="search"
              class="input"
              type="search"
              placeholder="Chit number, name or mobile"
              autocomplete="off"
              [value]="search()"
              (input)="onSearch($any($event.target).value)"
            />
          </div>
          <div class="field">
            <label class="field__label" for="status">Status</label>
            <select id="status" class="select" (change)="setStatus($any($event.target).value)">
              <option value="">All</option>
              @for (option of statusOptions; track option.value) {
                <option [value]="option.value" [selected]="status() === option.value">{{ option.label }}</option>
              }
            </select>
          </div>
          <div class="field">
            <label class="field__label" for="type">Saving towards</label>
            <select id="type" class="select" (change)="setType($any($event.target).value)">
              <option value="">Both</option>
              <option value="MONEY" [selected]="schemeType() === 'MONEY'">Money</option>
              <option value="GOLD" [selected]="schemeType() === 'GOLD'">Gold</option>
            </select>
          </div>
        </div>

        @if (loading()) {
          <p class="card__body text-muted">Loading...</p>
        } @else if (!rows().length) {
          <p class="card__body text-muted">No chits match this.</p>
        } @else {
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th>Chit No</th>
                  <th>Customer</th>
                  <th>Scheme</th>
                  <th class="num">Monthly</th>
                  <th class="num">Paid</th>
                  <th class="num">Put in</th>
                  <th class="num">Balance</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                @for (row of rows(); track row.id) {
                  <tr [class.row--cancelled]="row.status === 'CANCELLED'">
                    <td><a [routerLink]="['/chits', row.id]">{{ row.chitNumber }}</a></td>
                    <td>
                      {{ row.customerName }}
                      @if (row.customerMobile) {
                        <div class="text-muted">{{ row.customerMobile }}</div>
                      }
                    </td>
                    <td>{{ row.schemeName }}</td>
                    <td class="num">{{ row.monthlyAmount | inr: 'whole' }}</td>
                    <td class="num">
                      {{ paidByCustomer(row) }} / {{ row.payableInstalments }}
                      @if (hasBonus(row)) { <span class="text-muted">+ bonus</span> }
                    </td>
                    <td class="num">{{ row.paidAmount | inr: 'whole' }}</td>
                    <td class="num">
                      @if (row.schemeType === 'GOLD') {
                        {{ row.balanceGrams | weight }}
                      } @else {
                        {{ row.balanceAmount | inr: 'whole' }}
                      }
                    </td>
                    <td><app-chit-status-badge [status]="row.status" /></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <div class="card__body pager">
            <span class="text-muted">{{ total() }} chit(s)</span>
            <span>
              <button type="button" class="btn btn--sm" [disabled]="page() === 0" (click)="go(page() - 1)">Previous</button>
              <button type="button" class="btn btn--sm" [disabled]="last()" (click)="go(page() + 1)">Next</button>
            </span>
          </div>
        }
      </section>
    </div>
  `,
  styles: [
    `
      .filters { display: grid; grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr)); gap: var(--space-3); }
      .num { text-align: right; font-variant-numeric: tabular-nums; }
      .row--cancelled td { color: var(--text-muted); text-decoration: line-through; }
      .pager { display: flex; justify-content: space-between; align-items: center; gap: var(--space-3); }
      .table-wrap { overflow-x: auto; }
    `,
  ],
})
export class ChitListComponent {
  private readonly api = inject(ChitApiService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly permissions = Permissions;

  protected readonly statusOptions = (Object.keys(CHIT_STATUS_LABELS) as ChitStatus[]).map((value) => ({
    value,
    label: CHIT_STATUS_LABELS[value],
  }));

  protected readonly rows = signal<ChitSummary[]>([]);
  protected readonly loading = signal(false);
  protected readonly page = signal(0);
  protected readonly total = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly search = signal('');
  protected readonly status = signal<ChitStatus | ''>('');
  protected readonly schemeType = signal<ChitSchemeType | ''>('');

  protected readonly last = computed(() => this.page() + 1 >= this.totalPages());

  /** Typing a chit number should not send a request per keystroke. */
  private readonly typed = new Subject<string>();

  constructor() {
    this.typed.pipe(debounceTime(300), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.go(0));
    this.load();
  }

  protected onSearch(value: string): void {
    this.search.set(value);
    this.typed.next(value);
  }

  protected setStatus(value: string): void {
    this.status.set((value as ChitStatus) || '');
    this.go(0);
  }

  protected setType(value: string): void {
    this.schemeType.set((value as ChitSchemeType) || '');
    this.go(0);
  }

  /**
   * Months the customer paid.
   *
   * The stored count takes in the shop's twelfth once the chit matures, and
   * "12 / 11" in a column headed Paid reads as an overpayment. The bonus gets
   * its own mark instead.
   */
  protected paidByCustomer(row: ChitSummary): number {
    return Math.min(row.paidInstalments, row.payableInstalments);
  }

  protected hasBonus(row: ChitSummary): boolean {
    return row.paidInstalments > row.payableInstalments;
  }

  protected go(page: number): void {
    this.page.set(Math.max(0, page));
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.api
      .list(
        { page: this.page(), size: 20, sort: 'id', direction: 'desc' },
        {
          search: this.search().trim() || null,
          status: this.status() || null,
          schemeType: this.schemeType() || null,
        },
      )
      .subscribe({
        next: (result: Page<ChitSummary>) => {
          this.rows.set(result.content);
          this.total.set(result.totalElements);
          this.totalPages.set(result.totalPages);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }
}
