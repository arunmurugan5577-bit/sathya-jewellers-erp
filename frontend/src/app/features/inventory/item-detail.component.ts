import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { Permissions } from '../../core/auth/permissions';
import { ConfirmService } from '../../core/services/confirm.service';
import { NotificationService } from '../../core/services/notification.service';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { SpinnerComponent } from '../../shared/components/spinner.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { InventoryItem } from '../../shared/models/inventory.model';
import { PercentagePipe } from '../../shared/pipes/percentage.pipe';
import { WeightPipe } from '../../shared/pipes/weight.pipe';
import { InventoryService } from './inventory.service';

/**
 * One piece, in full.
 *
 * Includes the audit trail, which matters more here than anywhere else: an
 * inventory record is a statement about a physical object in a safe, and "who
 * entered this, and when" is the first question asked when the two disagree.
 */
@Component({
  selector: 'app-item-detail',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
    PageHeaderComponent,
    SpinnerComponent,
    StatusBadgeComponent,
    HasPermissionDirective,
    WeightPipe,
    PercentagePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      @if (loading()) {
        <app-spinner label="Loading item..." />
      } @else if (item(); as record) {
        <app-page-header
          [title]="record.serialNumber"
          [subtitle]="record.purityName + ' ' + record.itemTypeName + ' ' + record.categoryName"
        >
          <a class="btn" routerLink="/inventory">Back to list</a>
          <a
            *appHasPermission="permissions.LABEL_VIEW"
            class="btn"
            routerLink="/labels"
            [queryParams]="{ serial: record.serialNumber }"
          >
            Print label
          </a>
          <a
            *appHasPermission="permissions.INVENTORY_EDIT"
            class="btn"
            [routerLink]="['/inventory', record.id, 'edit']"
          >
            Edit
          </a>
          <button
            *appHasPermission="permissions.INVENTORY_EDIT"
            type="button"
            class="btn"
            [class.btn--danger]="record.active"
            (click)="toggleActive(record)"
          >
            {{ record.active ? 'Deactivate' : 'Activate' }}
          </button>
          <button
            *appHasPermission="permissions.INVENTORY_DELETE"
            type="button"
            class="btn btn--danger"
            (click)="remove(record)"
          >
            Delete
          </button>
        </app-page-header>

        <section class="card">
          <div class="card__header">
            <h2 class="card__title">Details</h2>
            @if (record.status === 'SOLD') {
              <span class="badge badge--info">Sold</span>
            } @else {
              <app-status-badge [active]="record.active" activeLabel="In stock" />
            }
          </div>
          <div class="card__body">
            <dl class="details">
              <div class="details__row">
                <dt>Serial number</dt>
                <dd class="table__serial">{{ record.serialNumber }}</dd>
              </div>
              <div class="details__row">
                <dt>Item type</dt>
                <dd>{{ record.itemTypeName }}</dd>
              </div>
              <div class="details__row">
                <dt>Purity</dt>
                <dd>{{ record.purityName }} ({{ record.purityValue }})</dd>
              </div>
              <div class="details__row">
                <dt>Category</dt>
                <dd>{{ record.categoryName }}</dd>
              </div>
              <div class="details__row">
                <dt>Sub category</dt>
                <dd>{{ record.subCategoryName || '-' }}</dd>
              </div>
              <div class="details__row">
                <dt>Size</dt>
                <dd>{{ record.size || '-' }}</dd>
              </div>
              <div class="details__row">
                <dt>{{ record.bulk ? 'Weight of the box' : 'Weight' }}</dt>
                <dd class="numeric">{{ record.weightGrams | weight }}</dd>
              </div>
              @if (record.bulk) {
                <div class="details__row">
                  <dt>Still in the box</dt>
                  <dd class="numeric">
                    {{ record.remainingWeightGrams | weight }}
                    @if (record.remainingWeightGrams < record.weightGrams) {
                      &middot; {{ record.weightGrams - record.remainingWeightGrams | weight }} sold
                    }
                  </dd>
                </div>
              }
              <div class="details__row">
                <dt>HSN / GST</dt>
                <dd>
                  @if (record.hsnCode) {
                    {{ record.hsnCode }} &middot; {{ record.gstPercentage | percentage }}
                  } @else {
                    <span class="text-muted">Not set</span>
                  }
                </dd>
              </div>
              <div class="details__row details__row--wide">
                <dt>Description</dt>
                <dd>{{ record.description || '-' }}</dd>
              </div>
            </dl>
          </div>
        </section>

        <section class="card">
          <div class="card__header"><h2 class="card__title">Audit</h2></div>
          <div class="card__body">
            <dl class="details">
              <div class="details__row">
                <dt>Added by</dt>
                <dd>{{ record.createdBy || '-' }}</dd>
              </div>
              <div class="details__row">
                <dt>Added on</dt>
                <dd>{{ record.createdAt | date: 'dd MMM yyyy, HH:mm' }}</dd>
              </div>
              <div class="details__row">
                <dt>Last changed by</dt>
                <dd>{{ record.updatedBy || '-' }}</dd>
              </div>
              <div class="details__row">
                <dt>Last changed on</dt>
                <dd>{{ record.updatedAt | date: 'dd MMM yyyy, HH:mm' }}</dd>
              </div>
            </dl>
          </div>
        </section>
      }
    </div>
  `,
  styles: [
    `
      .details {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: var(--space-4);
        margin: 0;
      }

      .details__row--wide {
        grid-column: 1 / -1;
      }

      dt {
        font-size: var(--text-xs);
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.04em;
        color: var(--text-muted);
        margin-bottom: var(--space-1);
      }

      dd {
        margin: 0;
        font-size: var(--text-md);
      }
    `,
  ],
})
export class ItemDetailComponent {
  private readonly inventory = inject(InventoryService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmService);
  private readonly router = inject(Router);

  readonly id = input.required<string>();

  protected readonly permissions = Permissions;
  protected readonly loading = signal(true);
  protected readonly item = signal<InventoryItem | null>(null);

  constructor() {
    effect(() => {
      const id = Number(this.id());
      if (Number.isNaN(id)) {
        return;
      }
      this.loading.set(true);
      this.inventory.get(id).subscribe({
        next: (item) => {
          this.item.set(item);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          void this.router.navigate(['/inventory']);
        },
      });
    });
  }

  protected async toggleActive(item: InventoryItem): Promise<void> {
    if (item.active) {
      const confirmed = await this.confirm.ask({
        title: `Deactivate ${item.serialNumber}?`,
        message:
          'The piece stays on record with all its details, but is no longer counted as current ' +
          'stock.',
        confirmLabel: 'Deactivate',
        danger: true,
      });
      if (!confirmed) {
        return;
      }
    }

    this.inventory.setActive(item.id, !item.active).subscribe({
      next: (updated) => {
        this.item.set(updated);
        this.notifications.success(
          `Item ${item.serialNumber} ${item.active ? 'deactivated' : 'activated'}.`,
        );
      },
    });
  }

  protected async remove(item: InventoryItem): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: `Delete ${item.serialNumber}?`,
      message:
        'This permanently removes the record. Delete only a piece that was entered by mistake - ' +
        'for a piece that physically existed, deactivate it instead so the history survives.',
      confirmLabel: 'Delete permanently',
      danger: true,
    });
    if (!confirmed) {
      return;
    }

    this.inventory.delete(item.id).subscribe({
      next: () => {
        this.notifications.success(`Item ${item.serialNumber} deleted.`);
        void this.router.navigate(['/inventory']);
      },
    });
  }
}
