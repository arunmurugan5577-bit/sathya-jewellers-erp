import { ChangeDetectionStrategy, Component, HostListener, input, output } from '@angular/core';

/**
 * Dialog shell used by every create / edit form.
 *
 * Forms open in a dialog rather than on their own route because master data is
 * edited in short bursts while reading the list - navigating away and back loses
 * the reader's place, their filters and their scroll position.
 *
 * Closing is deliberately restricted: Escape and the explicit Cancel button
 * work, a backdrop click does not. Losing a half-typed item because the mouse
 * slipped is exactly the kind of thing that makes people distrust a system.
 */
@Component({
  selector: 'app-modal',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="backdrop">
      <div class="modal" role="dialog" aria-modal="true" [attr.aria-label]="title()">
        <header class="modal__header">
          <h2 class="modal__title">{{ title() }}</h2>
          <button type="button" class="btn btn--ghost btn--icon" aria-label="Close" (click)="closed.emit()">
            &times;
          </button>
        </header>

        <div class="modal__body">
          <ng-content />
        </div>

        <footer class="modal__footer">
          <ng-content select="[modalActions]" />
        </footer>
      </div>
    </div>
  `,
  styles: [
    `
      .backdrop {
        position: fixed;
        inset: 0;
        z-index: 800;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: var(--space-4);
        background: rgba(20, 18, 15, 0.45);
        overflow-y: auto;
      }

      .modal {
        display: flex;
        flex-direction: column;
        width: min(720px, 100%);
        max-height: calc(100vh - 2 * var(--space-4));
        background: var(--surface-raised);
        border-radius: var(--radius-lg);
        box-shadow: var(--shadow-lg);
      }

      .modal__header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-4);
        padding: var(--space-4) var(--space-5);
        border-bottom: 1px solid var(--border-subtle);
      }

      .modal__title {
        font-size: var(--text-lg);
      }

      .modal__body {
        flex: 1;
        overflow-y: auto;
        padding: var(--space-5);
      }

      .modal__footer {
        display: flex;
        justify-content: flex-end;
        gap: var(--space-3);
        padding: var(--space-4) var(--space-5);
        border-top: 1px solid var(--border-subtle);
      }

      @media (max-width: 640px) {
        .backdrop {
          padding: 0;
          align-items: stretch;
        }

        .modal {
          width: 100%;
          max-height: 100vh;
          border-radius: 0;
        }
      }
    `,
  ],
})
export class ModalComponent {
  readonly title = input.required<string>();

  readonly closed = output<void>();

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    this.closed.emit();
  }
}
