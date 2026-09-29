import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { NotificationService } from '../../core/services/notification.service';

/**
 * Renders transient notifications.
 *
 * Lives at the application root so a toast survives the route change that often
 * follows the action which produced it - "Item saved" must still be readable
 * after the form closes and the list reloads.
 */
@Component({
  selector: 'app-toast-container',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toasts" role="status" aria-live="polite">
      @for (toast of notifications.toasts(); track toast.id) {
        <div class="toast toast--{{ toast.kind }}">
          <span class="toast__message">{{ toast.message }}</span>
          <button
            type="button"
            class="toast__close"
            aria-label="Dismiss"
            (click)="notifications.dismiss(toast.id)"
          >
            &times;
          </button>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .toasts {
        position: fixed;
        top: var(--space-4);
        right: var(--space-4);
        z-index: 1000;
        display: flex;
        flex-direction: column;
        gap: var(--space-2);
        max-width: min(420px, calc(100vw - 2 * var(--space-4)));
      }

      .toast {
        display: flex;
        align-items: flex-start;
        gap: var(--space-3);
        padding: var(--space-3) var(--space-4);
        border-radius: var(--radius-md);
        border-left: 3px solid transparent;
        background: var(--surface-raised);
        box-shadow: var(--shadow-md);
        font-size: var(--text-base);
        animation: slide-in 160ms ease-out;
      }

      .toast--success {
        border-left-color: var(--success);
      }
      .toast--error {
        border-left-color: var(--danger);
      }
      .toast--warning {
        border-left-color: var(--warning);
      }
      .toast--info {
        border-left-color: var(--info);
      }

      .toast__message {
        flex: 1;
      }

      .toast__close {
        border: none;
        background: none;
        color: var(--text-muted);
        font-size: var(--text-lg);
        line-height: 1;
        cursor: pointer;
        padding: 0;
      }

      @keyframes slide-in {
        from {
          opacity: 0;
          transform: translateX(16px);
        }
        to {
          opacity: 1;
          transform: translateX(0);
        }
      }

      @media (max-width: 640px) {
        .toasts {
          top: auto;
          bottom: var(--space-4);
          left: var(--space-4);
          right: var(--space-4);
          max-width: none;
        }
      }
    `,
  ],
})
export class ToastContainerComponent {
  protected readonly notifications = inject(NotificationService);
}
