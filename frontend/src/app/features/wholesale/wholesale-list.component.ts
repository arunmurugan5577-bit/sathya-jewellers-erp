import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { Permissions } from '../../core/auth/permissions';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { Page } from '../../shared/models/page.model';
import { WholesaleStatus, WholesaleSummary } from '../../shared/models/wholesale.model';
import { InrPipe } from '../../shared/pipes/inr.pipe';
import { WeightPipe } from '../../shared/pipes/weight.pipe';
import { WholesaleApiService } from './wholesale-api.service';

@Component({
  selector: 'app-wholesale-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, PageHeaderComponent, HasPermissionDirective, InrPipe, WeightPipe],
  template: `
    <div class="page">
      <app-page-header title="Wholesale estimates" subtitle="Gold sold by pure weight, against a running account.">
        <a class="btn btn--primary" routerLink="/wholesale/new" *appHasPermission="permissions.WHOLESALE_CREATE">
          + New estimate
        </a>
      </app-page-header>

      <section class="card">
        <div class="card__body filters">
          <div class="field">
            <label class="field__label" for="from">From</label>
            <input id="from" class="input" type="date" [value]="fromDate()" (change)="setFrom($any($event.target).value)" />
          </div>
          <div class="field">
            <label class="field__label" for="to">To</label>
            <input id="to" class="input" type="date" [value]="toDate()" (change)="setTo($any($event.target).value)" />
          </div>
          <div class="field">
            <label class="field__label" for="status">Status</label>
            <select id="status" class="select" (change)="setStatus($any($event.target).value)">
              <option value="">All</option>
              <option value="COMPLETED" [selected]="status() === 'COMPLETED'">Completed</option>
              <option value="CANCELLED" [selected]="status() === 'CANCELLED'">Cancelled</option>
            </select>
          </div>
        </div>

        @if (loading()) {
          <p class="card__body text-muted">Loading...</p>
        } @else if (!rows().length) {
          <p class="card__body text-muted">No estimates in this range.</p>
        } @else {
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th>Estimate No</th>
                  <th>Date</th>
                  <th>Party</th>
                  <th class="num">Pure Wt</th>
                  <th class="num">Total</th>
                  <th class="num">Closing Pure</th>
                  <th class="num">Closing Value</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                @for (row of rows(); track row.id) {
                  <tr [class.row--cancelled]="row.status === 'CANCELLED'">
                    <td><a [routerLink]="['/wholesale', row.id]">{{ row.estimateNumber }}</a></td>
                    <td>{{ row.estimateDate }}</td>
                    <td>{{ row.customerName }}</td>
                    <td class="num">{{ row.totalPureGrams | weight }} g</td>
                    <td class="num">{{ row.totalAmount | inr: 'whole' }}</td>
                    <td class="num">{{ row.closingPureGrams | weight }} g</td>
                    <td class="num">{{ row.closingValue | inr: 'whole' }}</td>
                    <td>{{ row.status }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <div class="card__body pager">
            <span class="text-muted">{{ total() }} estimate(s)</span>
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
export class WholesaleListComponent {
  private readonly api = inject(WholesaleApiService);
  private readonly auth = inject(AuthService);

  protected readonly permissions = Permissions;

  protected readonly rows = signal<WholesaleSummary[]>([]);
  protected readonly loading = signal(false);
  protected readonly page = signal(0);
  protected readonly total = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly fromDate = signal('');
  protected readonly toDate = signal('');
  protected readonly status = signal<WholesaleStatus | ''>('');

  protected readonly last = computed(() => this.page() + 1 >= this.totalPages());

  constructor() {
    this.load();
  }

  protected setFrom(value: string): void {
    this.fromDate.set(value);
    this.go(0);
  }

  protected setTo(value: string): void {
    this.toDate.set(value);
    this.go(0);
  }

  protected setStatus(value: string): void {
    this.status.set((value as WholesaleStatus) || '');
    this.go(0);
  }

  protected go(page: number): void {
    this.page.set(Math.max(0, page));
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.api
      .list(
        { page: this.page(), size: 20, sort: 'estimateDate', direction: 'desc' },
        {
          fromDate: this.fromDate() || null,
          toDate: this.toDate() || null,
          status: this.status() || null,
        },
      )
      .subscribe({
        next: (result: Page<WholesaleSummary>) => {
          this.rows.set(result.content);
          this.total.set(result.totalElements);
          this.totalPages.set(result.totalPages);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }
}
