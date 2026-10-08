import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, model, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';

import { SUPPRESS_ERROR_TOAST } from '../../core/interceptors/error.interceptor';
import { Permissions } from '../../core/auth/permissions';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { Customer, CustomerSummary } from '../../shared/models/sales.model';
import { CustomerApiService } from '../sales/sales-api.service';
import { CustomerFormComponent } from './customer-form.component';

/**
 * A lookup that failed, as opposed to one that found nobody.
 *
 * <p>The two look identical in a list of no results, and at a counter they are
 * opposite instructions: one means take the customer's details and add them,
 * the other means try again in a moment.
 */
const LOOKUP_FAILED = Symbol('lookup failed');

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
            <li class="result result--empty" [class.text-muted]="!failed()" [class.text-danger]="failed()">
              @if (searching()) {
                Searching...
              } @else if (failed()) {
                Could not reach the server. Check the connection and type again.
              } @else {
                No customer found.
              }
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
      .text-danger { color: var(--danger); }
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
  protected readonly failed = signal(false);
  protected readonly formOpen = signal(false);

  constructor() {
    this.terms$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((term) => {
          if (!term.trim()) {
            return of([] as CustomerSummary[]);
          }
          // catchError INSIDE the switchMap. Outside it, one failed lookup -
          // a dropped wifi connection, a restarting server - completes the
          // outer stream with an error and the search box is dead until the
          // page is reloaded. The counter would just see a box that has
          // stopped responding.
          return this.api
            .lookup(term.trim(), new HttpContext().set(SUPPRESS_ERROR_TOAST, true))
            .pipe(catchError(() => of(LOOKUP_FAILED)));
        }),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((results) => {
        this.failed.set(results === LOOKUP_FAILED);
        this.results.set(results === LOOKUP_FAILED ? [] : results);
        this.searching.set(false);
      });
  }

  protected onInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.term.set(value);
    // Set here rather than in the pipeline, which only runs after the debounce:
    // for that quarter second the list would otherwise read "No customer
    // found." about a search that has not been made yet.
    this.searching.set(value.trim().length > 0);
    this.failed.set(false);
    this.terms$.next(value);
  }

  protected select(option: CustomerSummary): void {
    this.customer.set(option);
  }

  protected clear(): void {
    this.customer.set(null);
    this.term.set('');
    this.results.set([]);
    this.searching.set(false);
    this.failed.set(false);
    // The stream has to be told, not just the signals. distinctUntilChanged
    // remembers the last term it saw, so without this, searching for the same
    // customer again after pressing Change is silently dropped and the list
    // sits on "No customer found." for a customer who plainly exists.
    this.terms$.next('');
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
