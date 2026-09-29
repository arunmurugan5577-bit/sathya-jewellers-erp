import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Inline loading indicator. */
@Component({
  selector: 'app-spinner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="spinner" [class.spinner--inline]="inline()" role="status">
      <span class="spinner__circle"></span>
      @if (label()) {
        <span class="spinner__label">{{ label() }}</span>
      }
    </div>
  `,
  styles: [
    `
      .spinner {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: var(--space-3);
        padding: var(--space-6);
        color: var(--text-muted);
      }

      .spinner--inline {
        padding: 0;
      }

      .spinner__circle {
        width: 18px;
        height: 18px;
        border: 2px solid var(--border-strong);
        border-top-color: var(--accent);
        border-radius: 50%;
        animation: spin 700ms linear infinite;
      }

      .spinner__label {
        font-size: var(--text-base);
      }

      @keyframes spin {
        to {
          transform: rotate(360deg);
        }
      }
    `,
  ],
})
export class SpinnerComponent {
  readonly label = input<string>();
  readonly inline = input(false);
}
