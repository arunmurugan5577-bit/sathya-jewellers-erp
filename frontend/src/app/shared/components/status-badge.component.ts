import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Active / inactive pill, so status reads the same on every list. */
@Component({
  selector: 'app-status-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="badge" [class.badge--success]="active()" [class.badge--muted]="!active()">
      {{ active() ? activeLabel() : inactiveLabel() }}
    </span>
  `,
})
export class StatusBadgeComponent {
  readonly active = input.required<boolean>();
  readonly activeLabel = input('Active');
  readonly inactiveLabel = input('Inactive');
}
