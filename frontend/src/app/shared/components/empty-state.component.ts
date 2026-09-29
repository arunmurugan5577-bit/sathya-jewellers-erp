import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Shown when a list has no rows.
 *
 * Takes a message rather than a fixed string so a screen can distinguish "you
 * have not added anything yet" from "nothing matches this filter" - two very
 * different situations that an identical empty table would blur.
 */
@Component({
  selector: 'app-empty-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="empty">
      <p class="empty__title">{{ title() }}</p>
      @if (message()) {
        <p class="empty__message">{{ message() }}</p>
      }
      <ng-content />
    </div>
  `,
  styles: [
    `
      .empty {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--space-2);
        padding: var(--space-7) var(--space-4);
        text-align: center;
      }

      .empty__title {
        font-weight: 600;
        color: var(--text-secondary);
      }

      .empty__message {
        color: var(--text-muted);
        font-size: var(--text-base);
        max-width: 42ch;
      }
    `,
  ],
})
export class EmptyStateComponent {
  readonly title = input('No records found');
  readonly message = input<string>();
}
