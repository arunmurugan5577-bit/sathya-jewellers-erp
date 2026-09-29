import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, model, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';

import { Permissions } from '../../core/auth/permissions';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { Customer, CustomerSummary } from '../../shared/models/sales.model';
import { CustomerApiService } from '../sales/sales-api.service';
import { CustomerFormComponent } from './customer-form.component';

/**
 * Find a customer by name, mobile or code - or add one without leaving the counter.
 * Two-way bound: `[(customer)]`.
 */
@Component({
  selector: 'app-customer-picker',
  standalone: true,
  imports: [CustomerFormComponent, HasPermissionDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (customer(); as selected) {
      <div class="selected">
        <div>
          <div class="selected__name">{{ selected.fullName }} <span class="text-muted">{{ selected.customerCode }}</span></div>
          <div class="text-secondary">{{ selected.mobileNumber || 'No mobile' }}</div>
          @if (selected.formattedAddress) {
            <div class="text-muted">{{ selected.formattedAddress }}</div>
          }
        </div>
        @if (!disabled()) {
          <button type="button" class="btn btn--sm" (click)="clear()">Change</button>
        }
      </div>
    } @else {
      <div class="search">
        <input
          class="input"
          type="search"
          placeholder="Search name, mobile or customer code"
          autocomplete="off"
          [value]="term()"
          (input)="onInput($event)"
          aria-label="Search customer"
        />
        <button
          *appHasPermission="permissions.CUSTOMER_CREATE"
          type="button"
          class="btn"
          (click)="formOpen.set(true)"
        >
          + New customer
        </button>
      </div>
      @if (term().trim().length > 0) {
        <ul class="results" role="listbox">
          @for (option of results(); track option.id) {
            <li>
              <button type="button" class="result" (click)="select(option)">
                <strong>{{ option.fullName }}</strong>
                <span class="text-secondary">{{ option.mobileNumber || '' }}</span>
                <span class="text-muted">{{ option.customerCode }}</span>
              </button>
            </li>
          } @empty {
            <li class="text-muted result result--empty">
              {{ searching() ? 'Searching...' : 'No customer found.' }}
            </li>
          }
        </ul>
      }
    }

    @if (formOpen()) {
      <app-customer-form [initialText]="term()" (saved)="onCreated($event)" (closed)="formOpen.set(false)" />
    }
  `,
  styles: [
    `
      .search { display: flex; gap: var(--space-2); flex-wrap: wrap; }
      .search .input { flex: 1; min-width: 12rem; }
      .results {
        list-style: none; margin: var(--space-2) 0 0; padding: 0;
        border: 1px solid var(--border-subtle); border-radius: var(--radius-md);
        max-height: 16rem; overflow-y: auto; background: var(--surface-raised);
      }
      .result {
        display: flex; gap: var(--space-3); flex-wrap: wrap; align-items: baseline;
        width: 100%; padding: var(--space-2) var(--space-3); border: 0; background: none;
        text-align: left; cursor: pointer; color: var(--text-primary); font: inherit;
      }
      button.result:hover { background: var(--surface-hover); }
      .result--empty { cursor: default; }
      .selected {
        display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-3);
        padding: var(--space-3); border: 1px solid var(--border-subtle); border-radius: var(--radius-md);
        background: var(--surface-sunken);
      }
      .selected__name { font-weight: 600; }
    `,
  ],
})
export class CustomerPickerComponent {
  private readonly api = inject(CustomerApiService);
  private readonly terms$ = new Subject<string>();

  readonly customer = model<CustomerSummary | null>(null);
  readonly disabled = input(false);

  protected readonly permissions = Permissions;
  protected readonly term = signal('');
  protected readonly results = signal<CustomerSummary[]>([]);
  protected readonly searching = signal(false);
  protected readonly formOpen = signal(false);

  constructor() {
    this.terms$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((term) => {
          this.searching.set(true);
          return term.trim() ? this.api.lookup(term.trim()) : of([]);
        }),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((results) => {
        this.results.set(results);
        this.searching.set(false);
      });
  }

  protected onInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.term.set(value);
    this.terms$.next(value);
  }

  protected select(option: CustomerSummary): void {
    this.customer.set(option);
  }

  protected clear(): void {
    this.customer.set(null);
    this.term.set('');
    this.results.set([]);
  }

  protected onCreated(created: Customer): void {
    this.formOpen.set(false);
    const address = [created.addressLine1, created.addressLine2, created.city, created.state, created.pincode]
      .filter((part) => !!part)
      .join(', ');
    this.customer.set({
      id: created.id,
      customerCode: created.customerCode,
      fullName: created.fullName,
      mobileNumber: created.mobileNumber,
      formattedAddress: address || null,
    });
  }
}
