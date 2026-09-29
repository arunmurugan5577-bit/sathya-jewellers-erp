import { HttpContext, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';

import { Permissions } from '../../core/auth/permissions';
import { SUPPRESS_ERROR_TOAST } from '../../core/interceptors/error.interceptor';
import { NotificationService } from '../../core/services/notification.service';
import { DataTableComponent } from '../../shared/components/data-table.component';
import { ModalComponent } from '../../shared/components/modal.component';
import { PageHeaderComponent } from '../../shared/components/page-header.component';
import { PaginatorComponent } from '../../shared/components/paginator.component';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { isApiError } from '../../shared/models/api-error.model';
import { InventoryFilters, InventoryItem } from '../../shared/models/inventory.model';
import { LabelProblem, LabelSettings } from '../../shared/models/label.model';
import { Lookup } from '../../shared/models/lookup.model';
import { DEFAULT_PAGE_SIZE, Page, emptyPage } from '../../shared/models/page.model';
import { InventoryService } from '../inventory/inventory.service';
import { CategoryService } from '../masters/category/category.service';
import { ItemTypeService } from '../masters/item-type/item-type.service';
import { PurityService } from '../masters/purity/purity.service';
import { SubCategoryService } from '../masters/sub-category/sub-category.service';
import { LabelSettingsComponent } from './label-settings.component';
import { LabelsApiService } from './labels-api.service';

const quiet = () => new HttpContext().set(SUPPRESS_ERROR_TOAST, true);

/**
 * Barcode labels for jewellery tags.
 *
 * The pieces come from the existing inventory list - same endpoint, same filters
 * - and the label itself is built by the server, so the barcode always carries
 * the serial number on the stock record and nothing the browser could alter.
 *
 * Printing goes through the browser's own print path: the label document is a
 * page the exact size of the tag, handed to the Windows driver for the TVS
 * LP 46 NEO. Talking to the printer directly from a browser tab is not
 * something a browser will do without a helper program running beside it.
 */
@Component({
  selector: 'app-label-print',
  standalone: true,
  imports: [
    PageHeaderComponent,
    DataTableComponent,
    PaginatorComponent,
    ModalComponent,
    HasPermissionDirective,
    LabelSettingsComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="stack">
      <app-page-header
        title="Label printing"
        subtitle="Barcode tags for stock. The barcode holds the serial number only."
      >
        @if (settings(); as current) {
          <span class="chip">Short name: <strong>{{ current.shopShortName }}</strong></span>
          <span class="chip">Printer: <strong>{{ printerName() }}</strong></span>
          <button
            *appHasPermission="permissions.LABEL_EDIT"
            type="button"
            class="btn"
            (click)="settingsOpen.set(true)"
          >
            Label settings
          </button>
        }
      </app-page-header>

      <section class="card">
        <div class="toolbar">
          <div class="toolbar__field">
            <label class="toolbar__label" for="serial">Serial number</label>
            <input
              id="serial"
              class="input serial"
              type="search"
              placeholder="e.g. 000123"
              inputmode="numeric"
              [value]="filters().serialNumber ?? ''"
              (input)="onSerialInput($event)"
            />
          </div>
          <div class="toolbar__field">
            <label class="toolbar__label" for="itemType">Item type</label>
            <select id="itemType" class="select" (change)="onItemTypeChange($event)">
              <option value="">All</option>
              @for (type of itemTypes(); track type.id) {
                <option [value]="type.id" [selected]="filters().itemTypeId === type.id">{{ type.name }}</option>
              }
            </select>
          </div>
          <div class="toolbar__field">
            <label class="toolbar__label" for="purity">Purity</label>
            <select id="purity" class="select" [disabled]="!filters().itemTypeId" (change)="onFilterChange('purityId', $event)">
              <option value="">{{ filters().itemTypeId ? 'All' : 'Select an item type' }}</option>
              @for (purity of purities(); track purity.id) {
                <option [value]="purity.id" [selected]="filters().purityId === purity.id">{{ purity.name }}</option>
              }
            </select>
          </div>
          <div class="toolbar__field">
            <label class="toolbar__label" for="category">Category</label>
            <select id="category" class="select" (change)="onCategoryChange($event)">
              <option value="">All</option>
              @for (category of categories(); track category.id) {
                <option [value]="category.id" [selected]="filters().categoryId === category.id">{{ category.name }}</option>
              }
            </select>
          </div>
          <div class="toolbar__field">
            <label class="toolbar__label" for="subCategory">Sub category</label>
            <select id="subCategory" class="select" [disabled]="!filters().categoryId" (change)="onFilterChange('subCategoryId', $event)">
              <option value="">{{ filters().categoryId ? 'All' : 'Select a category' }}</option>
              @for (subCategory of subCategories(); track subCategory.id) {
                <option [value]="subCategory.id" [selected]="filters().subCategoryId === subCategory.id">{{ subCategory.name }}</option>
              }
            </select>
          </div>
          <div class="toolbar__field">
            <label class="toolbar__label" for="stock">Stock</label>
            <select id="stock" class="select" (change)="onStatusChange($event)">
              <option value="AVAILABLE" selected>In stock</option>
              <option value="SOLD">Sold</option>
              <option value="">All</option>
            </select>
          </div>
        </div>

        <div class="selection">
          <span class="selection__count">Selected: <strong>{{ selected().size }}</strong></span>
          <button type="button" class="btn btn--sm" [disabled]="page().content.length === 0" (click)="selectPage()">
            Select all on page
          </button>
          <button type="button" class="btn btn--sm" [disabled]="selected().size === 0" (click)="clearSelection()">
            Clear selection
          </button>
          <div class="toolbar__spacer"></div>
          <label class="copies">
            Copies each
            <input class="input numeric" type="number" min="1" max="20" [value]="copies()" (input)="onCopiesChange($event)" />
          </label>
          @if (tagsAcross() > 1) {
            <label class="copies" title="Start further along the row to use up tags left from an earlier print">
              Start at tag
              <select class="select" (change)="startColumn.set(+$any($event.target).value)">
                @for (tag of tagPositions(); track tag) {
                  <option [value]="tag" [selected]="tag === startColumn()">{{ tag }}</option>
                }
              </select>
            </label>
          }
          <button type="button" class="btn" [disabled]="selected().size === 0 || busy()" (click)="openPreview()">
            Preview
          </button>
          <button
            *appHasPermission="permissions.LABEL_CREATE"
            type="button"
            class="btn btn--primary"
            [disabled]="selected().size === 0 || busy()"
            (click)="print()"
          >
            {{ busy() ? 'Printing...' : printButtonLabel() }}
          </button>
          <button
            *appHasPermission="permissions.LABEL_CREATE"
            type="button"
            class="btn btn--ghost btn--sm"
            title="Use the browser's print dialog instead - for a machine where the label printer is not installed"
            [disabled]="selected().size === 0 || busy()"
            (click)="print(true)"
          >
            Print via browser
          </button>
        </div>

        @if (problems().length > 0) {
          <div class="alert alert--error problems">
            <strong>These labels cannot be printed:</strong>
            <ul>
              @for (problem of problems(); track problem.message) {
                <li>{{ problem.serialNumber ? problem.serialNumber + ': ' : '' }}{{ problem.message }}</li>
              }
            </ul>
          </div>
        }
        @if (statusMessage()) {
          <div class="alert alert--info problems">{{ statusMessage() }}</div>
        }

        <app-data-table
          [loading]="loading()"
          [isEmpty]="page().content.length === 0"
          emptyTitle="No items match these filters"
          emptyMessage="Widen the filters to find the pieces you want to tag."
        >
          <table class="table">
            <thead>
              <tr>
                <th class="pick">
                  <input
                    type="checkbox"
                    aria-label="Select all on page"
                    [checked]="allOnPageSelected()"
                    (change)="togglePage($event)"
                  />
                </th>
                <th>Serial</th>
                <th>Item type</th>
                <th>Purity</th>
                <th>Category</th>
                <th>Sub category</th>
                <th>Status</th>
                <th class="td--actions">Label</th>
              </tr>
            </thead>
            <tbody>
              @for (item of page().content; track item.id) {
                <tr [class.row--selected]="selected().has(item.id)">
                  <td class="pick">
                    <input
                      type="checkbox"
                      [attr.aria-label]="'Select ' + item.serialNumber"
                      [checked]="selected().has(item.id)"
                      (change)="toggle(item)"
                    />
                  </td>
                  <td class="table__serial">{{ item.serialNumber }}</td>
                  <td>{{ item.itemTypeName }}</td>
                  <td>{{ item.purityName }}</td>
                  <td>{{ item.categoryName }}</td>
                  <td>{{ item.subCategoryName || '-' }}</td>
                  <td>
                    @if (item.status === 'SOLD') {
                      <span class="badge badge--info">Sold</span>
                    } @else {
                      <span class="badge badge--success">In stock</span>
                    }
                  </td>
                  <td class="td--actions">
                    <button type="button" class="btn btn--sm" (click)="previewOne(item)">View</button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </app-data-table>

        <app-paginator [page]="page()" (pageChange)="reload($event)" (sizeChange)="onSizeChange($event)" />
      </section>
    </div>

    @if (previewOpen()) {
      <app-modal title="Label preview" (closed)="previewOpen.set(false)">
        <div class="preview">
          @if (previewHtml()) {
            <!-- Shown at twice size so the counter can read it; the document
                 inside is still at true label size, which is what prints. -->
            <div
              class="preview__stage"
              [style.width.px]="previewWidthMm() * 3.78 * previewScale()"
              [style.height.px]="previewHeightMm() * 3.78 * previewScale()"
            >
              <iframe
                class="preview__frame"
                title="Label preview"
                scrolling="no"
                [srcdoc]="previewHtml()!"
                [style.width.mm]="previewWidthMm()"
                [style.height.mm]="previewHeightMm()"
                [style.transform]="'scale(' + previewScale() + ')'"
              ></iframe>
            </div>
          } @else if (previewError()) {
            <div class="alert alert--error">{{ previewError() }}</div>
          } @else {
            <p class="text-muted">Building the label...</p>
          }
          <p class="text-muted preview__note">
            One printed page: {{ previewWidthMm() }} &times; {{ previewHeightMm() }} mm, {{ tagsAcross() }} tag{{ tagsAcross() === 1 ? '' : 's' }} across.
          </p>
        </div>
        <ng-container modalActions>
          @if (previewIds().length > 1) {
            <div class="row preview__nav">
              <button type="button" class="btn btn--sm" [disabled]="previewIndex() === 0" (click)="step(-1)">
                &lsaquo; Previous
              </button>
              <span class="text-muted">Label {{ previewIndex() + 1 }} of {{ previewIds().length }}</span>
              <button
                type="button"
                class="btn btn--sm"
                [disabled]="previewIndex() >= previewIds().length - 1"
                (click)="step(1)"
              >
                Next &rsaquo;
              </button>
            </div>
          }
          <button type="button" class="btn btn--ghost" (click)="previewOpen.set(false)">Close</button>
          <button
            *appHasPermission="permissions.LABEL_CREATE"
            type="button"
            class="btn btn--primary"
            [disabled]="busy()"
            (click)="print()"
          >
            {{ busy() ? 'Printing...' : printButtonLabel() }}
          </button>
        </ng-container>
      </app-modal>
    }

    @if (settingsOpen() && settings()) {
      <app-label-settings
        [settings]="settings()!"
        (saved)="onSettingsSaved($event)"
        (closed)="settingsOpen.set(false)"
      />
    }
  `,
  styles: [
    `
      .chip {
        align-self: center;
        padding: var(--space-1) var(--space-3);
        border-radius: var(--radius-pill);
        background: var(--surface-sunken);
        font-size: var(--text-sm);
      }
      .selection {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--space-2);
        padding: var(--space-3) var(--space-5);
        border-top: 1px solid var(--border-subtle);
        border-bottom: 1px solid var(--border-subtle);
        background: var(--surface-sunken);
      }
      .selection__count { margin-right: var(--space-2); }
      .copies { display: flex; align-items: center; gap: var(--space-2); font-size: var(--text-sm); color: var(--text-secondary); }
      .copies .input { width: 4.5rem; }
      .problems { margin: var(--space-4) var(--space-5) 0; }
      .problems ul { margin: var(--space-2) 0 0 var(--space-4); }
      .serial { font-family: var(--font-mono); letter-spacing: 0.06em; }
      .pick { width: 1%; text-align: center; }
      .row--selected td { background: var(--accent-soft); }
      .preview { display: flex; flex-direction: column; align-items: center; gap: var(--space-3); max-width: 100%; }
      /* A wide roll on a narrow screen scrolls inside the preview, not the dialog. */
      .preview__stage { max-width: 100%; overflow: auto; }
      .preview__stage { overflow: hidden; background: #fff; border-radius: var(--radius-sm); }
      .preview__frame { border: 0; background: #fff; transform-origin: top left; }
      .preview__note { font-size: var(--text-sm); }
      .preview__nav { align-items: center; gap: var(--space-3); margin-right: auto; }
    `,
  ],
})
export class LabelPrintComponent {
  private readonly api = inject(LabelsApiService);
  private readonly inventory = inject(InventoryService);
  private readonly notifications = inject(NotificationService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly serialInput$ = new Subject<string>();

  protected readonly permissions = Permissions;
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly page = signal<Page<InventoryItem>>(emptyPage<InventoryItem>());
  protected readonly filters = signal<InventoryFilters>({ status: 'AVAILABLE' });
  protected readonly selected = signal<Map<number, InventoryItem>>(new Map());
  protected readonly copies = signal(1);
  /** Which tag of the row to begin at, so a part-used row is not thrown away. */
  protected readonly startColumn = signal(1);
  protected readonly settings = signal<LabelSettings | null>(null);
  protected readonly settingsOpen = signal(false);
  protected readonly problems = signal<LabelProblem[]>([]);
  protected readonly statusMessage = signal<string | null>(null);

  protected readonly itemTypes = signal<Lookup[]>([]);
  protected readonly categories = signal<Lookup[]>([]);
  protected readonly subCategories = signal<Lookup[]>([]);
  protected readonly purities = signal<Lookup[]>([]);

  protected readonly previewOpen = signal(false);
  protected readonly previewIds = signal<number[]>([]);
  protected readonly previewIndex = signal(0);
  protected readonly previewHtml = signal<SafeHtml | null>(null);
  protected readonly previewError = signal<string | null>(null);

  protected readonly allOnPageSelected = computed(() => {
    const rows = this.page().content;
    return rows.length > 0 && rows.every((item) => this.selected().has(item.id));
  });

  protected readonly printButtonLabel = computed(() => {
    const count = this.selected().size * this.copies();
    return count <= 1 ? 'Print label' : `Print ${count} labels`;
  });

  /** Shown beside the title so the counter knows where the labels will come out. */
  protected readonly printerName = computed(() => this.settings()?.printerName || 'default printer');

  protected readonly tagsAcross = computed(() => Number(this.settings()?.labelsAcross ?? 1));
  protected readonly tagPositions = computed(() =>
    Array.from({ length: this.tagsAcross() }, (unused, index) => index + 1),
  );

  /** The preview shows a whole printed page, which is one row of tags. */
  protected readonly previewWidthMm = computed(
    () => Number(this.settings()?.labelWidthMm ?? 50) * this.tagsAcross(),
  );
  protected readonly previewHeightMm = computed(() => Number(this.settings()?.labelHeightMm ?? 25));
  /** Scales the preview up for legibility, but never wider than the dialog. */
  protected readonly previewScale = computed(() => {
    const widthPx = this.previewWidthMm() * 3.78;
    return Math.max(1, Math.min(2.5, 400 / Math.max(widthPx, 1)));
  });

  constructor() {
    this.serialInput$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe((serialNumber) => {
        this.filters.update((current) => ({ ...current, serialNumber: serialNumber || null }));
        this.reload(0);
      });

    inject(ItemTypeService).lookup().subscribe((values) => this.itemTypes.set(values));
    inject(CategoryService).lookup().subscribe((values) => this.categories.set(values));
    this.api.settings().subscribe((settings) => this.settings.set(settings));

    // Deep link from the inventory screen: /labels?serial=000123
    const serial = inject(ActivatedRoute).snapshot.queryParamMap.get('serial');
    if (serial) {
      this.filters.update((current) => ({ ...current, serialNumber: serial, status: null }));
      this.reload(0, (result) => {
        const match = result.content.find((item) => item.serialNumber === serial);
        if (match) {
          this.toggle(match);
        }
      });
    } else {
      this.reload(0);
    }
  }

  private readonly purityService = inject(PurityService);
  private readonly subCategoryService = inject(SubCategoryService);

  // ------------------------------------------------------------- listing ---

  protected reload(page = this.page().page, then?: (result: Page<InventoryItem>) => void): void {
    this.loading.set(true);
    this.inventory
      .list({ page, size: this.page().size || DEFAULT_PAGE_SIZE, sort: 'serialNumber', direction: 'asc' }, this.filters())
      .subscribe({
        next: (result) => {
          this.page.set(result);
          this.loading.set(false);
          then?.(result);
        },
        error: () => this.loading.set(false),
      });
  }

  protected onSerialInput(event: Event): void {
    this.serialInput$.next((event.target as HTMLInputElement).value.trim());
  }

  protected onFilterChange(key: 'purityId' | 'subCategoryId', event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.filters.update((current) => ({ ...current, [key]: value ? Number(value) : null }));
    this.reload(0);
  }

  protected onItemTypeChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    const itemTypeId = value ? Number(value) : null;
    this.filters.update((current) => ({ ...current, itemTypeId, purityId: null }));
    this.purities.set([]);
    if (itemTypeId) {
      this.purityService.lookupByItemType(itemTypeId).subscribe((values) => this.purities.set(values));
    }
    this.reload(0);
  }

  protected onCategoryChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    const categoryId = value ? Number(value) : null;
    this.filters.update((current) => ({ ...current, categoryId, subCategoryId: null }));
    this.subCategories.set([]);
    if (categoryId) {
      this.subCategoryService.lookupByCategory(categoryId).subscribe((values) => this.subCategories.set(values));
    }
    this.reload(0);
  }

  protected onStatusChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.filters.update((current) => ({
      ...current,
      status: value === 'AVAILABLE' || value === 'SOLD' ? value : null,
    }));
    this.reload(0);
  }

  protected onSizeChange(size: number): void {
    this.page.update((current) => ({ ...current, size }));
    this.reload(0);
  }

  // ----------------------------------------------------------- selection ---

  protected toggle(item: InventoryItem): void {
    this.selected.update((current) => {
      const next = new Map(current);
      if (next.has(item.id)) {
        next.delete(item.id);
      } else {
        next.set(item.id, item);
      }
      return next;
    });
    this.clearMessages();
  }

  protected togglePage(event: Event): void {
    if ((event.target as HTMLInputElement).checked) {
      this.selectPage();
    } else {
      this.selected.update((current) => {
        const next = new Map(current);
        this.page().content.forEach((item) => next.delete(item.id));
        return next;
      });
    }
  }

  /** Selection survives paging and filtering, so a run can be gathered from several searches. */
  protected selectPage(): void {
    this.selected.update((current) => {
      const next = new Map(current);
      this.page().content.forEach((item) => next.set(item.id, item));
      return next;
    });
    this.clearMessages();
  }

  protected clearSelection(): void {
    this.selected.set(new Map());
    this.clearMessages();
  }

  protected onCopiesChange(event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    this.copies.set(Number.isFinite(value) && value >= 1 ? Math.min(20, Math.floor(value)) : 1);
  }

  // ------------------------------------------------------------- preview ---

  protected previewOne(item: InventoryItem): void {
    this.previewIds.set([item.id]);
    this.previewIndex.set(0);
    this.previewOpen.set(true);
    this.loadPreview();
  }

  protected openPreview(): void {
    this.previewIds.set([...this.selected().keys()]);
    this.previewIndex.set(0);
    this.previewOpen.set(true);
    this.loadPreview();
  }

  protected step(direction: number): void {
    this.previewIndex.update((index) =>
      Math.min(Math.max(index + direction, 0), this.previewIds().length - 1),
    );
    this.loadPreview();
  }

  private loadPreview(): void {
    const id = this.previewIds()[this.previewIndex()];
    if (id === undefined) {
      return;
    }
    this.previewHtml.set(null);
    this.previewError.set(null);
    this.api.previewDocument({ inventoryItemIds: [id], startColumn: this.startColumn() }, quiet()).subscribe({
      next: (html) => this.previewHtml.set(this.sanitizer.bypassSecurityTrustHtml(html)),
      error: (error: unknown) => this.previewError.set(describe(error)),
    });
  }

  // ------------------------------------------------------------- printing ---

  /**
   * Checks the selection, then sends it to the label printer.
   *
   * <p>Straight from the server to the Windows printer: no print dialog to leave
   * on the office printer, and the page is the tag's own size. Printing through
   * the browser is still there for a machine where no printer is set up.
   */
  protected print(viaBrowser = false): void {
    const ids = [...this.selected().keys()];
    if (ids.length === 0) {
      this.notifications.warning('Select at least one item to print.');
      return;
    }
    this.busy.set(true);
    this.clearMessages();
    const request = { inventoryItemIds: ids, copies: this.copies(), startColumn: this.startColumn() };

    this.api.preview(request, quiet()).subscribe({
      next: (preview) => {
        if (preview.problems.length > 0) {
          this.busy.set(false);
          this.problems.set(preview.problems);
          this.notifications.error('Some selected items cannot be printed. See the list above.');
          return;
        }
        if (viaBrowser) {
          this.api.printDocument(request, quiet()).subscribe({
            next: (html) => {
              this.busy.set(false);
              this.previewOpen.set(false);
              this.sendToBrowserPrint(html, ids.length * this.copies());
            },
            error: (error: unknown) => {
              this.busy.set(false);
              this.notifications.error(describe(error));
            },
          });
          return;
        }
        this.api.print(request, quiet()).subscribe({
          next: (result) => {
            this.busy.set(false);
            this.previewOpen.set(false);
            const printed = `${result.printed} label${result.printed === 1 ? '' : 's'}`;
            this.statusMessage.set(`${printed} printed on ${result.printer}.`);
            this.notifications.success(`${printed} sent to ${result.printer}.`);
          },
          error: (error: unknown) => {
            this.busy.set(false);
            this.notifications.error(describe(error));
          },
        });
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.notifications.error(describe(error));
      },
    });
  }

  private sendToBrowserPrint(html: string, labelCount: number): void {
    const frame = document.createElement('iframe');
    frame.setAttribute('aria-hidden', 'true');
    frame.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden;';
    frame.srcdoc = html;
    frame.onload = () => {
      const win = frame.contentWindow;
      if (!win) {
        this.notifications.error(
          'Unable to print the labels. Check the printer connection and printer settings, then try again.',
        );
        frame.remove();
        return;
      }
      try {
        win.focus();
        win.print();
        this.statusMessage.set(
          `${labelCount} label${labelCount === 1 ? '' : 's'} sent to the browser's print dialog. ` +
            'Choose the label printer there, with margins set to none and scale 100%.',
        );
        this.notifications.success(`${labelCount} label${labelCount === 1 ? '' : 's'} sent to the print dialog.`);
      } catch {
        this.notifications.error(
          'Unable to print the labels. Check the printer connection and printer settings, then try again.',
        );
      }
      // The dialog is modal but the document must stay alive behind it.
      setTimeout(() => frame.remove(), 60_000);
    };
    document.body.appendChild(frame);
  }

  protected onSettingsSaved(settings: LabelSettings): void {
    this.settings.set(settings);
    this.settingsOpen.set(false);
    if (this.previewOpen()) {
      this.loadPreview();
    }
  }

  private clearMessages(): void {
    this.problems.set([]);
    this.statusMessage.set(null);
  }
}

function describe(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    // A text/html request carries its error body as a string, so the JSON
    // envelope arrives unparsed.
    let body: unknown = error.error;
    if (typeof body === 'string') {
      try {
        body = JSON.parse(body);
      } catch {
        body = null;
      }
    }
    if (isApiError(body)) {
      const fields = Object.values(body.fieldErrors ?? {});
      return fields.length ? fields.join(' ') : body.message;
    }
  }
  return 'Unable to prepare the labels. Please try again.';
}
