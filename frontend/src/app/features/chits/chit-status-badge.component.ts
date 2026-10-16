import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { CHIT_STATUS_LABELS, ChitStatus } from '../../shared/models/chit.model';

const TONES: Readonly<Record<ChitStatus, string>> = {
  ACTIVE: 'badge--info',
  /* Waiting on the shop, not on the customer - worth standing out from the
     running ones, since somebody has to act on it. */
  COMPLETE: 'badge--warning',
  MATURED: 'badge--success',
  CLOSED: 'badge--muted',
  CANCELLED: 'badge--muted',
};

/** Where a chit has got to, worded as the counter would say it. */
@Component({
  selector: 'app-chit-status-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge" [class]="'badge ' + tone()">{{ label() }}</span>`,
})
export class ChitStatusBadgeComponent {
  readonly status = input.required<ChitStatus>();

  protected readonly label = computed(() => CHIT_STATUS_LABELS[this.status()] ?? this.status());
  protected readonly tone = computed(() => TONES[this.status()] ?? 'badge--muted');
}
