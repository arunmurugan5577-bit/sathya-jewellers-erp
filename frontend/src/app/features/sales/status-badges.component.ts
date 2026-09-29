import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

const STYLES: Record<string, { label: string; tone: string }> = {
  PAID: { label: 'Paid', tone: 'badge--success' },
  PARTIAL: { label: 'Part paid', tone: 'badge--info' },
  UNPAID: { label: 'Unpaid', tone: 'badge--danger' },
  CANCELLED: { label: 'Cancelled', tone: 'badge--muted' },
  COMPLETED: { label: 'Completed', tone: 'badge--success' },
  AVAILABLE: { label: 'Available', tone: 'badge--success' },
  PARTIALLY_USED: { label: 'Partly used', tone: 'badge--info' },
  USED: { label: 'Used', tone: 'badge--muted' },
  SOLD: { label: 'Sold', tone: 'badge--info' },
};

/** One badge for every document and payment state, so they read the same everywhere. */
@Component({
  selector: 'app-payment-status-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge" [class]="'badge ' + style().tone">{{ style().label }}</span>`,
})
export class PaymentStatusBadgeComponent {
  readonly status = input.required<string>();
  protected readonly style = computed(() => STYLES[this.status()] ?? { label: this.status(), tone: 'badge--muted' });
}
