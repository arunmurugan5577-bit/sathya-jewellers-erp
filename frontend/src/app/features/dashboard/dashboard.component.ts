import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { Permissions } from '../../core/auth/permissions';
import { ApiService } from '../../core/services/api.service';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { SpinnerComponent } from '../../shared/components/spinner.component';
import { DashboardSummary } from '../../shared/models/shop.model';
// Used to format the stock-weight metric in TypeScript, not in the template.
import { WeightPipe } from '../../shared/pipes/weight.pipe';

interface MetricCard {
  label: string;
  value: string;
  hint?: string;
  route?: string;
  /** SVG path data for the tile's corner icon. */
  icon: string;
}

/** Inline icon set - eleven glyphs do not justify an icon library. */
const tileIcons = {
  box: 'M3 7l9-4 9 4v10l-9 4-9-4V7zm9 4l9-4m-9 4L3 7m9 4v10',
  check: 'M20 6L9 17l-5-5',
  scale: 'M12 3v18M5 7h14M7 7l-3 7h6zM17 7l-3 7h6z',
  layers: 'M12 2l9 5-9 5-9-5 9-5zm9 10l-9 5-9-5',
  users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
} as const;

/**
 * Landing page.
 *
 * Kept deliberately thin for this release: counters only, no charts. The card
 * list is data-driven so that adding a metric later is one entry here plus one
 * field on the server's summary - not a new layout.
 *
 * Cards appear only for metrics the server returned, and the server only returns
 * what the user's permissions allow. A staff member without user administration
 * simply does not see a user count.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [NgTemplateOutlet, RouterLink, PageHeaderComponent, SpinnerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-header
        [title]="greeting()"
        subtitle="Here is where your shop stands today."
      />

      @if (loading()) {
        <app-spinner label="Loading summary..." />
      } @else {
        @if (cards().length === 0) {
          <div class="card">
            <div class="card__body">
              <p class="text-secondary">
                You do not yet have access to any module. An administrator can grant you
                permissions from the Users screen.
              </p>
            </div>
          </div>
        } @else {
          <div class="metrics">
            @for (card of cards(); track card.label) {
              <ng-template #tile>
                <span class="metric__head">
                  <span class="eyebrow">{{ card.label }}</span>
                  <span class="icon-badge icon-badge--gold" aria-hidden="true">
                    <svg viewBox="0 0 24 24"><path [attr.d]="card.icon" /></svg>
                  </span>
                </span>
                <span class="figure">{{ card.value }}</span>
                @if (card.hint) {
                  <span class="metric__hint">{{ card.hint }}</span>
                }
              </ng-template>

              @if (card.route) {
                <a class="metric metric--link" [routerLink]="card.route">
                  <ng-container [ngTemplateOutlet]="tile" />
                </a>
              } @else {
                <div class="metric">
                  <ng-container [ngTemplateOutlet]="tile" />
                </div>
              }
            }
          </div>
        }

        @if (quickActions().length > 0) {
          <section class="card">
            <div class="card__header">
              <h2 class="card__title">Quick actions</h2>
            </div>
            <div class="card__body">
              <div class="actions">
                @for (action of quickActions(); track action.route) {
                  <a class="btn" [routerLink]="action.route">{{ action.label }}</a>
                }
              </div>
            </div>
          </section>
        }
      }
    </div>
  `,
  styles: [
    `
      .metrics {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
        gap: var(--space-4);
      }

      .metric {
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        padding: var(--space-5);
        background: var(--surface-card);
        border: 1px solid var(--border-subtle);
        border-radius: var(--radius-lg);
        box-shadow: var(--shadow-sm);
        text-decoration: none;
        color: inherit;
        transition:
          border-color var(--transition-fast),
          box-shadow var(--transition-fast);
      }

      .metric--link:hover {
        border-color: var(--gold);
        box-shadow: var(--shadow-md);
        text-decoration: none;
      }

      /* Label left, icon right - the label leads because the number is
       * meaningless without it. */
      .metric__head {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: var(--space-3);
      }

      .metric__hint {
        font-size: var(--text-xs);
        color: var(--text-muted);
      }

      .actions {
        display: flex;
        flex-wrap: wrap;
        gap: var(--space-3);
      }
    `,
  ],
})
export class DashboardComponent {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly weight = new WeightPipe();

  protected readonly loading = signal(true);
  protected readonly summary = signal<DashboardSummary>({});

  protected readonly greeting = computed(() => {
    const name = this.auth.user()?.fullName?.split(' ')[0] ?? 'there';
    return `Welcome back, ${name}`;
  });

  protected readonly cards = computed<MetricCard[]>(() => {
    const summary = this.summary();
    const cards: MetricCard[] = [];

    if (summary.totalItems !== undefined) {
      cards.push({
        label: 'Total items',
        value: String(summary.totalItems),
        hint: 'Every piece ever recorded',
        icon: tileIcons.box,
        route: '/inventory',
      });
      cards.push({
        label: 'Active items',
        value: String(summary.activeItems ?? 0),
        hint: 'Currently in stock',
        icon: tileIcons.check,
        route: '/inventory',
      });
    }
    if (summary.activeWeightGrams !== undefined) {
      cards.push({
        label: 'Stock weight',
        value: this.weight.transform(summary.activeWeightGrams, ''),
        hint: 'Grams, active items',
        icon: tileIcons.scale,
      });
    }
    if (summary.totalCategories !== undefined) {
      cards.push({
        label: 'Categories',
        value: String(summary.totalCategories),
        hint: 'Active',
        icon: tileIcons.layers,
        route: '/masters/categories',
      });
    }
    if (summary.totalUsers !== undefined) {
      cards.push({
        label: 'Users',
        value: String(summary.totalUsers),
        hint: `${summary.activeUsers ?? 0} active`,
        icon: tileIcons.users,
        route: '/masters/users',
      });
    }
    return cards;
  });

  protected readonly quickActions = computed(() => {
    this.auth.permissions();
    const actions: { label: string; route: string }[] = [];
    if (this.auth.has(Permissions.INVENTORY_CREATE)) {
      actions.push({ label: 'Add inventory item', route: '/inventory/new' });
    }
    if (this.auth.has(Permissions.INVENTORY_VIEW)) {
      actions.push({ label: 'Browse inventory', route: '/inventory' });
    }
    if (this.auth.has(Permissions.USER_CREATE)) {
      actions.push({ label: 'Add user', route: '/masters/users' });
    }
    return actions;
  });

  constructor() {
    this.api.get<DashboardSummary>('/dashboard/summary').subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
