import { HttpContext, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { SUPPRESS_ERROR_TOAST } from '../../../core/interceptors/error.interceptor';
import { NotificationService } from '../../../core/services/notification.service';
import { FieldErrorComponent } from '../../../shared/components/field-error.component';
import { PageHeaderComponent } from '../../../shared/components/page-header.component';
import { isApiError } from '../../../shared/models/api-error.model';
import { InventoryService } from '../../inventory/inventory.service';

/**
 * Where inventory serial numbers start.
 *
 * <p>One number, set when the shop decides where its tags begin. After that
 * every piece takes the next one automatically, so this screen exists to be
 * used rarely - on the day the shop starts numbering, and when a run has to be
 * moved past a block of tags that were printed elsewhere.
 */
@Component({
  selector: 'app-serial-numbering',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PageHeaderComponent, FieldErrorComponent],
  template: `
    <div class="page">
      <app-page-header
        title="Serial numbering"
        subtitle="Where inventory serial numbers start. Every piece then takes the next one automatically." />

      <section class="card">
        <div class="card__header"><h2 class="card__title">Inventory serial numbers</h2></div>

        @if (loading()) {
          <p class="card__body text-muted">Loading...</p>
        } @else {
          <div class="card__body">
            <dl class="facts">
              <dt>Numbering starts at</dt>
              <dd class="serial">{{ padded(startsAt()) }}</dd>
              <dt>The next piece will be</dt>
              <dd class="serial serial--next">{{ nextSerial() }}</dd>
            </dl>

            @if (skipping()) {
              <p class="note">
                {{ padded(startsAt()) }} to {{ previous(nextSerial()) }} are already on pieces in stock,
                so the next tag skips to <strong>{{ nextSerial() }}</strong>. Nothing is ever reused —
                a number already printed on a tag is stepped over.
              </p>
            }

            <form class="form" [formGroup]="form" (ngSubmit)="save()">
              <div class="field">
                <label class="field__label field__label--required" for="nextValue">Start numbering from</label>
                <input
                  id="nextValue"
                  class="input numeric"
                  type="number"
                  min="1"
                  max="999999"
                  step="1"
                  formControlName="nextValue" />
                <p class="field__hint">
                  A whole number from 1 to 999999. Tags carry at least three digits, so 1 is
                  <strong>001</strong>. After 999 they simply get longer &mdash; 1000, 1001.
                </p>
                <app-field-error [control]="form.controls.nextValue" label="Starting number" />
              </div>

              @if (error()) {
                <p class="field__error">{{ error() }}</p>
              }

              <button type="submit" class="btn btn--primary" [disabled]="saving() || form.invalid">
                {{ saving() ? 'Saving...' : 'Save' }}
              </button>
            </form>
          </div>
        }
      </section>
    </div>
  `,
  styles: [
    `
      .facts {
        display: grid;
        grid-template-columns: auto 1fr;
        gap: var(--space-2) var(--space-5);
        margin: 0 0 var(--space-4);
        align-items: baseline;
      }
      .facts dt { color: var(--text-secondary); }
      .facts dd { margin: 0; }
      .serial {
        font-variant-numeric: tabular-nums;
        letter-spacing: 0.1em;
        font-weight: 700;
        font-size: var(--text-md);
      }
      .serial--next { color: var(--brand); font-size: var(--text-lg); }
      .note {
        background: color-mix(in srgb, var(--warning, #c08a00) 12%, transparent);
        border-radius: var(--radius-sm);
        padding: var(--space-3) var(--space-4);
        margin: 0 0 var(--space-4);
        font-size: var(--text-sm);
      }
      .form { max-width: 20rem; display: flex; flex-direction: column; gap: var(--space-3); }
    `,
  ],
})
export class SerialNumberingComponent {
  private readonly inventory = inject(InventoryService);
  private readonly notifications = inject(NotificationService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly startsAt = signal(1);
  protected readonly nextSerial = signal('000001');

  /** True when the run has to jump over numbers that are already out there. */
  protected readonly skipping = computed(() => this.padded(this.startsAt()) !== this.nextSerial());

  protected readonly form = this.formBuilder.group({
    nextValue: this.formBuilder.control<number | null>(null, [
      Validators.required,
      Validators.min(1),
      Validators.max(999999),
    ]),
  });

  constructor() {
    this.load();
  }

  protected padded(value: number): string {
    // A floor, not a cap: 999 is followed by 1000, which pads to itself.
    return String(value).padStart(3, '0');
  }

  /** The last taken number before the one that will be issued, for the note. */
  protected previous(serial: string): string {
    return this.padded(Math.max(1, Number(serial) - 1));
  }

  private load(): void {
    this.inventory.serialCounter().subscribe({
      next: (counter) => {
        this.startsAt.set(counter.nextValue);
        this.nextSerial.set(counter.nextSerialNumber);
        this.form.controls.nextValue.setValue(counter.nextValue);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected save(): void {
    const value = this.form.controls.nextValue.value;
    if (this.form.invalid || value === null) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.inventory
      .setSerialCounter(value, new HttpContext().set(SUPPRESS_ERROR_TOAST, true))
      .subscribe({
        next: (saved) => {
          this.saving.set(false);
          this.startsAt.set(saved.nextValue);
          this.nextSerial.set(saved.nextSerialNumber);
          this.notifications.success(`The next piece will be ${saved.nextSerialNumber}.`);
        },
        error: (err: unknown) => {
          this.saving.set(false);
          this.error.set(
            err instanceof HttpErrorResponse && isApiError(err.error)
              ? Object.values(err.error.fieldErrors ?? {}).join(' ') || err.error.message
              : 'Could not save the starting number.',
          );
        },
      });
  }
}
