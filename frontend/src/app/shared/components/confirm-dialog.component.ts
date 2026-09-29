import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { ConfirmService } from '../../core/services/confirm.service';

/**
 * The confirmation prompt shown before any destructive action.
 *
 * Rendered once in the shell; every call site goes through
 * {@link ConfirmService}, so no screen has to build its own dialog and none can
 * skip the prompt by accident.
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (confirm.pending(); as request) {
      <div class="backdrop" (click)="confirm.respond(false)">
        <div
          class="dialog"
          role="alertdialog"
          aria-modal="true"
          [attr.aria-label]="request.title"
          (click)="$event.stopPropagation()"
        >
          <h2 class="dialog__title">{{ request.title }}</h2>
          <p class="dialog__message">{{ request.message }}</p>
          <div class="dialog__actions">
            <button type="button" class="btn" (click)="confirm.respond(false)">
              {{ request.cancelLabel ?? 'Cancel' }}
            </button>
            <button
              type="button"
              class="btn"
              [class.btn--danger]="request.danger"
              [class.btn--primary]="!request.danger"
              (click)="confirm.respond(true)"
            >
              {{ request.confirmLabel ?? 'Confirm' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [
    `
      .backdrop {
        position: fixed;
        inset: 0;
        z-index: 900;
        display: grid;
        place-items: center;
        padding: var(--space-4);
        background: rgba(20, 18, 15, 0.45);
      }

      .dialog {
        width: min(420px, 100%);
        padding: var(--space-5);
        background: var(--surface-raised);
        border-radius: var(--radius-lg);
        box-shadow: var(--shadow-lg);
      }

      .dialog__title {
        font-size: var(--text-lg);
        margin-bottom: var(--space-3);
      }

      .dialog__message {
        color: var(--text-secondary);
        margin-bottom: var(--space-5);
      }

      .dialog__actions {
        display: flex;
        justify-content: flex-end;
        gap: var(--space-3);
      }
    `,
  ],
})
export class ConfirmDialogComponent {
  protected readonly confirm = inject(ConfirmService);
}
