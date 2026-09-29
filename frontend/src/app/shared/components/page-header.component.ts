import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Title, optional subtitle and an action slot. Every screen starts with one. */
@Component({
  selector: 'app-page-header',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="page-header">
      <div class="page-header__text">
        <h1>{{ title() }}</h1>
        @if (subtitle()) {
          <p class="page-header__subtitle">{{ subtitle() }}</p>
        }
      </div>
      <div class="page-header__actions">
        <ng-content />
      </div>
    </header>
  `,
  styles: [
    `
      .page-header {
        display: flex;
        flex-wrap: wrap;
        align-items: flex-start;
        justify-content: space-between;
        gap: var(--space-4);
      }

      .page-header__subtitle {
        margin-top: var(--space-1);
        color: var(--text-muted);
        font-size: var(--text-base);
      }

      .page-header__actions {
        display: flex;
        flex-wrap: wrap;
        gap: var(--space-2);
      }
    `,
  ],
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string>();
}
