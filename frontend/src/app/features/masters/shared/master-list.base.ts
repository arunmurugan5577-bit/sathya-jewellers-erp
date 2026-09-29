import { DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subject, debounceTime, distinctUntilChanged } from 'rxjs';

import { CrudApiService } from '../../../core/services/crud-api.service';
import { QueryParams } from '../../../core/services/api.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NotificationService } from '../../../core/services/notification.service';
import { DEFAULT_PAGE_SIZE, Page, emptyPage } from '../../../shared/models/page.model';

/**
 * State and behaviour shared by every master list screen.
 *
 * All five master lists do the same things: page and sort on the server, debounce
 * a search box, filter by status, confirm before deactivating, and refuse to
 * delete something that is still referenced. Writing that five times would
 * guarantee that they drift - one forgetting the confirmation, another
 * paginating in the browser.
 *
 * Subclasses supply the service, the entity name for messages, and the template.
 * Anything genuinely specific to one master (the category filter on sub
 * categories, say) is added there.
 */
export abstract class MasterListPage<TEntity extends { id: number; active: boolean }, TRequest> {
  protected readonly notifications = inject(NotificationService);
  protected readonly confirm = inject(ConfirmService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly searchInput$ = new Subject<string>();

  protected readonly loading = signal(true);
  protected readonly page = signal<Page<TEntity>>(emptyPage<TEntity>());
  protected readonly search = signal('');
  protected readonly statusFilter = signal<'' | 'true' | 'false'>('');
  protected readonly sortField = signal('name');
  protected readonly sortDirection = signal<'asc' | 'desc'>('asc');

  /** The record being edited, or null when the dialog is closed. */
  protected readonly editing = signal<TEntity | null>(null);
  protected readonly dialogOpen = signal(false);

  /** The API client for this master. */
  protected abstract readonly service: CrudApiService<TEntity, TRequest>;

  /** Singular, capitalised - "Item type". Used in confirmations and toasts. */
  protected abstract readonly entityName: string;

  /** Extra query parameters a subclass wants applied to the list call. */
  protected extraFilters(): QueryParams {
    return {};
  }

  protected constructor() {
    // Debounced so typing in the search box does not fire a request per keystroke.
    this.searchInput$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((term) => {
        this.search.set(term);
        this.reload(0);
      });
  }

  // ------------------------------------------------------------- loading ---

  protected reload(page = this.page().page): void {
    this.loading.set(true);

    this.service
      .list(
        {
          page,
          size: this.page().size || DEFAULT_PAGE_SIZE,
          sort: this.sortField(),
          direction: this.sortDirection(),
        },
        {
          search: this.search() || undefined,
          active: this.statusFilter() === '' ? undefined : this.statusFilter(),
          ...this.extraFilters(),
        },
      )
      .subscribe({
        next: (result) => {
          this.page.set(result);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  protected onSearchInput(event: Event): void {
    this.searchInput$.next((event.target as HTMLInputElement).value);
  }

  protected onStatusChange(event: Event): void {
    this.statusFilter.set((event.target as HTMLSelectElement).value as '' | 'true' | 'false');
    this.reload(0);
  }

  protected onPageChange(page: number): void {
    this.reload(page);
  }

  protected onSizeChange(size: number): void {
    this.page.update((current) => ({ ...current, size }));
    this.reload(0);
  }

  /** Clicking a column header sorts by it, and clicking again reverses it. */
  protected toggleSort(field: string): void {
    if (this.sortField() === field) {
      this.sortDirection.update((direction) => (direction === 'asc' ? 'desc' : 'asc'));
    } else {
      this.sortField.set(field);
      this.sortDirection.set('asc');
    }
    this.reload(0);
  }

  protected sortIndicator(field: string): string {
    if (this.sortField() !== field) {
      return '';
    }
    return this.sortDirection() === 'asc' ? ' ↑' : ' ↓';
  }

  protected hasFilters(): boolean {
    return this.search() !== '' || this.statusFilter() !== '';
  }

  protected clearFilters(): void {
    this.search.set('');
    this.statusFilter.set('');
    this.reload(0);
  }

  // -------------------------------------------------------------- dialog ---

  protected openCreate(): void {
    this.editing.set(null);
    this.dialogOpen.set(true);
  }

  protected openEdit(entity: TEntity): void {
    this.editing.set(entity);
    this.dialogOpen.set(true);
  }

  protected closeDialog(): void {
    this.dialogOpen.set(false);
    this.editing.set(null);
  }

  protected onSaved(): void {
    const wasEditing = this.editing() !== null;
    this.closeDialog();
    this.notifications.success(`${this.entityName} ${wasEditing ? 'updated' : 'created'}.`);
    this.reload();
  }

  /** Bound and handed to the form component, which owns the submit lifecycle. */
  protected readonly saveHandler = (request: TRequest): Observable<TEntity> => {
    const editing = this.editing();
    return editing ? this.service.update(editing.id, request) : this.service.create(request);
  };

  // ------------------------------------------------------------ commands ---

  /**
   * Deactivation, always confirmed.
   *
   * Reactivating is not confirmed - it restores access rather than removing it,
   * and a prompt for a harmless action trains people to dismiss prompts.
   */
  protected async toggleActive(entity: TEntity): Promise<void> {
    if (entity.active) {
      const confirmed = await this.confirm.ask({
        title: `Deactivate ${this.entityName.toLowerCase()}?`,
        message:
          `It will stay on records that already reference it, but will no longer be ` +
          `available for selection on new entries.`,
        confirmLabel: 'Deactivate',
        danger: true,
      });
      if (!confirmed) {
        return;
      }
    }

    this.service.setActive(entity.id, !entity.active).subscribe({
      next: () => {
        this.notifications.success(
          `${this.entityName} ${entity.active ? 'deactivated' : 'activated'}.`,
        );
        this.reload();
      },
    });
  }

  /**
   * Hard delete.
   *
   * Offered because a record keyed in by mistake should be removable, but the
   * server refuses once anything references it and answers with a message saying
   * so - which the error interceptor shows.
   */
  protected async remove(entity: TEntity, label: string): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: `Delete ${label}?`,
      message:
        `This permanently removes the record. It is refused if anything already ` +
        `references it - deactivate it instead in that case.`,
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!confirmed) {
      return;
    }

    this.service.delete(entity.id).subscribe({
      next: () => {
        this.notifications.success(`${this.entityName} deleted.`);
        this.reload();
      },
    });
  }
}
