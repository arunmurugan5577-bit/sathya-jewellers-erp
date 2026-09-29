import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { EmptyStateComponent } from './empty-state.component';
import { SpinnerComponent } from './spinner.component';

/**
 * Table shell handling the three states every list has: loading, empty, and
 * showing rows.
 *
 * The caller projects its own `<table>`, because a generic column-definition
 * table ends up either too rigid for the inventory grid or so configurable that
 * reading it is harder than reading the markup it replaced. What is genuinely
 * shared is the state handling and the horizontal scroll container - so that is
 * what this owns.
 */
@Component({
  selector: 'app-data-table',
  standalone: true,
  imports: [SpinnerComponent, EmptyStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading()) {
      <app-spinner label="Loading..." />
    } @else if (isEmpty()) {
      <app-empty-state [title]="emptyTitle()" [message]="emptyMessage()">
        <ng-content select="[emptyAction]" />
      </app-empty-state>
    } @else {
      <div class="table-wrapper">
        <ng-content />
      </div>
    }
  `,
})
export class DataTableComponent {
  readonly loading = input(false);
  readonly isEmpty = input(false);
  readonly emptyTitle = input('No records found');
  readonly emptyMessage = input<string>();
}
