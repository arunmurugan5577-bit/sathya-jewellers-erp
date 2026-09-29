import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { PAGE_SIZE_OPTIONS, Page } from '../models/page.model';

/**
 * Pagination controls for a server-paginated list.
 *
 * Emits intent - "go to page 3", "show 50 per page" - and never holds the data
 * itself. The page it renders came from the server, which is the only way a list
 * of fifty thousand pieces stays fast.
 */
@Component({
  selector: 'app-paginator',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="paginator">
      <div class="paginator__summary">
        @if (page().totalElements === 0) {
          No records
        } @else {
          Showing <strong>{{ firstRow() }}</strong
          >&ndash;<strong>{{ lastRow() }}</strong> of
          <strong>{{ page().totalElements }}</strong>
        }
      </div>

      <div class="paginator__controls">
        <label class="paginator__size">
          <span class="text-muted">Rows</span>
          <select
            class="select paginator__select"
            [value]="page().size"
            (change)="onSizeChange($event)"
            aria-label="Rows per page"
          >
            @for (option of sizeOptions; track option) {
              <option [value]="option">{{ option }}</option>
            }
          </select>
        </label>

        <button
          type="button"
          class="btn btn--sm"
          [disabled]="page().first"
          (click)="pageChange.emit(page().page - 1)"
        >
          Previous
        </button>

        <span class="paginator__position numeric">
          {{ page().totalPages === 0 ? 0 : page().page + 1 }} / {{ page().totalPages }}
        </span>

        <button
          type="button"
          class="btn btn--sm"
          [disabled]="page().last"
          (click)="pageChange.emit(page().page + 1)"
        >
          Next
        </button>
      </div>
    </div>
  `,
  styles: [
    `
      .paginator {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        justify-content: space-between;
        gap: var(--space-3);
        padding: var(--space-3) var(--space-5);
        border-top: 1px solid var(--border-subtle);
        font-size: var(--text-sm);
        color: var(--text-secondary);
      }

      .paginator__controls {
        display: flex;
        align-items: center;
        gap: var(--space-3);
      }

      .paginator__size {
        display: flex;
        align-items: center;
        gap: var(--space-2);
      }

      .paginator__select {
        width: auto;
        min-height: 30px;
        padding-right: var(--space-5);
        background-position:
          calc(100% - 15px) 13px,
          calc(100% - 10px) 13px;
      }

      .paginator__position {
        min-width: 64px;
        text-align: center;
      }

      @media (max-width: 640px) {
        .paginator {
          justify-content: center;
        }
      }
    `,
  ],
})
export class PaginatorComponent<T> {
  readonly page = input.required<Page<T>>();

  readonly pageChange = output<number>();
  readonly sizeChange = output<number>();

  protected readonly sizeOptions = PAGE_SIZE_OPTIONS;

  protected readonly firstRow = computed(() => this.page().page * this.page().size + 1);

  protected readonly lastRow = computed(() =>
    Math.min((this.page().page + 1) * this.page().size, this.page().totalElements),
  );

  protected onSizeChange(event: Event): void {
    this.sizeChange.emit(Number((event.target as HTMLSelectElement).value));
  }
}
