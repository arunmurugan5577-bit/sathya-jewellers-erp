import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { Permissions } from '../../core/auth/permissions';
import { NotificationService } from '../../core/services/notification.service';
import { ModalComponent } from '../../shared/components/modal.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { SpinnerComponent } from '../../shared/components/spinner.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { isApiError } from '../../shared/models/api-error.model';
import { OldMetalTransaction } from '../../shared/models/sales.model';
import { InrPipe } from '../../shared/pipes/inr.pipe';
import { OldMetalApiService } from '../sales/sales-api.service';
import { OldMetalBillComponent } from './old-metal-bill.component';
import { PaymentStatusBadgeComponent } from '../sales/status-badges.component';

/** A purchase bill, on screen and as printed ("PURCHASE BILL"), plus its usage and cancellation. */
@Component({
  selector: 'app-old-metal-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, PageHeaderComponent, SpinnerComponent, ModalComponent, HasPermissionDirective, InrPipe, PaymentStatusBadgeComponent, OldMetalBillComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (bill(); as b) {
      <div class="stack">
        <div class="no-print">
          <app-page-header [title]="'Purchase bill ' + b.transactionNumber" [subtitle]="b.customerName">
            <a class="btn btn--ghost" routerLink="/old-metal">All purchase bills</a>
            <button type="button" class="btn btn--primary" (click)="print()">Print</button>
            @if (b.status === 'AVAILABLE') {
              <button *appHasPermission="permissions.OLD_METAL_DELETE" type="button" class="btn btn--danger" (click)="cancelOpen.set(true)">Cancel bill</button>
            }
            <!-- Offered on a cancelled bill too: clearing out the ones the shop
                 need not keep is the other half of cancelling. -->
            <button *appHasPermission="permissions.OLD_METAL_DELETE" type="button" class="btn btn--danger" (click)="deleteOpen.set(true)">Delete bill</button>
          </app-page-header>
          <div class="summary">
            <app-payment-status-badge [status]="b.status" />
            <span>Total <strong>{{ b.totalAmount | inr }}</strong></span>
            <span>Used on sales <strong>{{ b.usedAmount | inr }}</strong></span>
            <span>Available <strong>{{ b.availableAmount | inr }}</strong></span>
          </div>
          @if (b.status === 'CANCELLED') {
            <div class="alert alert--warning">Cancelled by {{ b.cancelledBy }} on {{ b.cancelledAt | date: 'dd MMM yyyy, HH:mm' }}: {{ b.cancelReason }}</div>
          }
        </div>

        <app-old-metal-bill [bill]="b" />
      </div>

      @if (deleteOpen()) {
        <app-modal [title]="'Delete ' + b.transactionNumber" (closed)="deleteOpen.set(false)">
          <p>
            <strong>This erases the purchase bill.</strong> It will not appear in the books or in a
            report - unlike cancelling, which keeps it on record.
          </p>
          <p class="text-muted">
            {{ b.transactionNumber }} is freed for the next bill if it was the last number issued.
            An older number stays spent.
          </p>
          <p class="text-muted">
            A bill whose value has been applied to a sale cannot be deleted. Cancel that sale first.
          </p>
          <div class="field">
            <label class="field__label field__label--required" for="confirmNumber">
              Type {{ b.transactionNumber }} to confirm
            </label>
            <input id="confirmNumber" class="input" autocomplete="off"
                   (input)="deleteConfirm.set($any($event.target).value)" />
          </div>
          @if (error()) { <div class="alert alert--error">{{ error() }}</div> }
          <ng-container modalActions>
            <button type="button" class="btn btn--ghost" (click)="deleteOpen.set(false)">Keep bill</button>
            <button type="button" class="btn btn--danger"
                    [disabled]="busy() || deleteConfirm().trim() !== b.transactionNumber"
                    (click)="remove(b)">Delete for good</button>
          </ng-container>
        </app-modal>
      }

      @if (cancelOpen()) {
        <app-modal [title]="'Cancel ' + b.transactionNumber" (closed)="cancelOpen.set(false)">
          <div class="field">
            <label class="field__label field__label--required" for="reason">Reason</label>
            <textarea id="reason" class="textarea" rows="3" maxlength="500" (input)="reason.set($any($event.target).value)"></textarea>
          </div>
          @if (error()) { <div class="alert alert--error">{{ error() }}</div> }
          <ng-container modalActions>
            <button type="button" class="btn btn--ghost" (click)="cancelOpen.set(false)">Keep bill</button>
            <button type="button" class="btn btn--danger" [disabled]="!reason().trim()" (click)="cancel(b)">Cancel bill</button>
          </ng-container>
        </app-modal>
      }
    } @else {
      <app-spinner />
    }
  `,
  styles: [
    `
      .summary { display: flex; flex-wrap: wrap; gap: var(--space-3) var(--space-6); align-items: center; margin: var(--space-3) 0; }
    `,
  ],
})
export class OldMetalDetailComponent implements OnInit {
  private readonly api = inject(OldMetalApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  protected readonly permissions = Permissions;
  protected readonly bill = signal<OldMetalTransaction | null>(null);
  protected readonly cancelOpen = signal(false);
  protected readonly deleteOpen = signal(false);
  /** Typed back by the user, so a stray click cannot erase a bill. */
  protected readonly deleteConfirm = signal('');
  protected readonly busy = signal(false);
  protected readonly reason = signal('');
  protected readonly error = signal<string | null>(null);


  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    const autoPrint = this.route.snapshot.queryParamMap.get('print') === '1';
    this.api.get(id).subscribe({
      next: (bill) => {
        this.bill.set(bill);
        if (autoPrint) {
          void this.router.navigate([], { queryParams: {}, replaceUrl: true });
          setTimeout(() => window.print(), 300);
        }
      },
      error: () => void this.router.navigate(['/old-metal']),
    });
  }

  protected print(): void {
    window.print();
  }

  protected remove(bill: OldMetalTransaction): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.delete(bill.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.deleteOpen.set(false);
        this.notifications.success(`Purchase bill ${bill.transactionNumber} deleted.`);
        void this.router.navigate(['/old-metal']);
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.error.set(
          err instanceof HttpErrorResponse && isApiError(err.error)
            ? err.error.message
            : 'Could not delete the purchase bill. Try again.',
        );
      },
    });
  }

  protected cancel(bill: OldMetalTransaction): void {
    this.error.set(null);
    this.api.cancel(bill.id, this.reason().trim()).subscribe({
      next: (updated) => {
        this.bill.set(updated);
        this.cancelOpen.set(false);
        this.notifications.success(`Purchase bill ${bill.transactionNumber} cancelled.`);
      },
      error: (error: unknown) =>
        this.error.set(error instanceof HttpErrorResponse && isApiError(error.error) ? error.error.message : 'Could not cancel.'),
    });
  }
}
