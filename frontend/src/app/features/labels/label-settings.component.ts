import { HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { SUPPRESS_ERROR_TOAST } from '../../core/interceptors/error.interceptor';
import { NotificationService } from '../../core/services/notification.service';
import { FieldErrorComponent } from '../../shared/components/field-error.component';
import { ModalComponent } from '../../shared/components/modal.component';
import { LABEL_ROTATIONS, LabelSettings } from '../../shared/models/label.model';
import { applyServerErrors, clearServerErrors, markAllTouched } from '../../shared/utils/form-errors';
import { LabelsApiService } from './labels-api.service';

/**
 * Label and printer calibration.
 *
 * Everything the printed tag measures is here, in millimetres, because lining a
 * thermal label up with a physical jewellery tag is done by printing one,
 * looking at it, and nudging a number - not by changing code.
 */
@Component({
  selector: 'app-label-settings',
  standalone: true,
  imports: [ReactiveFormsModule, ModalComponent, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-modal title="Label printing settings" (closed)="closed.emit()">
      <form class="form-grid" [formGroup]="form" (ngSubmit)="save()" id="labelSettingsForm">
        <div class="field field--full">
          <label class="field__label field__label--required" for="shopShortName">Shop short name</label>
          <input id="shopShortName" class="input short" formControlName="shopShortName" maxlength="8" />
          <p class="field__hint">Printed beside the barcode, e.g. SJ. Saved once and used on every label.</p>
          <app-field-error [control]="form.controls.shopShortName" label="Short name" />
        </div>

        <div class="field field--full">
          <label class="field__label" for="printerName">Label printer</label>
          <select id="printerName" class="select" formControlName="printerName">
            <option value="">Use this computer's default printer</option>
            @for (printer of printers(); track printer) {
              <option [value]="printer">{{ printer }}</option>
            }
          </select>
          <p class="field__hint">
            Labels are sent straight to this printer - no print dialog. The printer must be installed on the
            computer running the application.
          </p>
          <app-field-error [control]="form.controls.printerName" label="Printer" />
        </div>

        <h3 class="group field--full">Label size</h3>
        <div class="field">
          <label class="field__label field__label--required" for="printMode">Where labels print</label>
          <select id="printMode" class="select" formControlName="printMode">
            <option value="DIRECT">Printer on this server</option>
            <option value="AGENT">Printer at the shop (via the print agent)</option>
          </select>
          <p class="field__hint">
            Choose the shop option when the application is hosted online. Pages are then queued for the
            print agent running on the counter PC.
          </p>
        </div>

        <div class="field">
          <label class="field__label field__label--required" for="shopMark">Shop mark</label>
          <select id="shopMark" class="select" formControlName="shopMark">
            <option value="LOGO">Shop logo</option>
            <option value="TEXT">Short name</option>
            <option value="NONE">Neither</option>
          </select>
          <p class="field__hint">What is printed to the left of the barcode.</p>
        </div>
        @if (form.controls.shopMark.value === 'LOGO') {
          <div class="field">
            <label class="field__label field__label--required" for="shopLogoHeightMm">Logo size (mm)</label>
            <input id="shopLogoHeightMm" class="input numeric" type="number" step="0.5" min="2" max="40"
                   formControlName="shopLogoHeightMm" />
            <p class="field__hint">The box is square. Below about 4 mm the monogram stops reading.</p>
            <app-field-error [control]="form.controls.shopLogoHeightMm" label="Logo size" />
          </div>
        }

        <div class="field">
          <label class="field__label field__label--required" for="labelWidthMm">Tag width (mm)</label>
          <input id="labelWidthMm" class="input numeric" type="number" step="0.5" formControlName="labelWidthMm" />
          <app-field-error [control]="form.controls.labelWidthMm" label="Width" />
        </div>
        <div class="field">
          <label class="field__label field__label--required" for="labelHeightMm">Tag length (mm)</label>
          <input id="labelHeightMm" class="input numeric" type="number" step="0.5" formControlName="labelHeightMm" />
          <app-field-error [control]="form.controls.labelHeightMm" label="Height" />
        </div>
        <div class="field">
          <label class="field__label field__label--required" for="labelsAcross">Tags across the roll</label>
          <input id="labelsAcross" class="input numeric" type="number" min="1" max="10" formControlName="labelsAcross" />
          <p class="field__hint">One printed page is one row of this many tags.</p>
          <app-field-error [control]="form.controls.labelsAcross" label="Tags across" />
        </div>
        <div class="field">
          <label class="field__label field__label--required" for="contentWidthMm">Printable length (mm)</label>
          <input id="contentWidthMm" class="input numeric" type="number" step="0.5" formControlName="contentWidthMm" />
          <p class="field__hint">How far along the tag print can go. Set this to the head if the rest is neck.</p>
          <app-field-error [control]="form.controls.contentWidthMm" label="Printable length" />
        </div>
        <div class="field">
          <label class="field__label field__label--required" for="contentHeightMm">Printable height (mm)</label>
          <input id="contentHeightMm" class="input numeric" type="number" step="0.5" formControlName="contentHeightMm" />
          <p class="field__hint">The head of the tag. The tail below it stays blank.</p>
          <app-field-error [control]="form.controls.contentHeightMm" label="Printable height" />
        </div>
        <div class="field">
          <label class="field__label" for="marginTopMm">Top margin (mm)</label>
          <input id="marginTopMm" class="input numeric" type="number" step="0.5" formControlName="marginTopMm" />
        </div>
        <div class="field">
          <label class="field__label" for="marginLeftMm">Left margin (mm)</label>
          <input id="marginLeftMm" class="input numeric" type="number" step="0.5" formControlName="marginLeftMm" />
          <p class="field__hint">
            Where the print starts along the tag. Go negative to pull it back towards the leading edge when
            the printer starts too far in.
          </p>
          <app-field-error [control]="form.controls.marginLeftMm" label="Left margin" />
        </div>
        <div class="field">
          <label class="field__label" for="offsetXMm">Print offset X (mm)</label>
          <input id="offsetXMm" class="input numeric" type="number" step="0.5" formControlName="offsetXMm" />
          <p class="field__hint">Use if the print sits left or right of the tag.</p>
        </div>
        <div class="field">
          <label class="field__label" for="offsetYMm">Print offset Y (mm)</label>
          <input id="offsetYMm" class="input numeric" type="number" step="0.5" formControlName="offsetYMm" />
        </div>
        <div class="field">
          <label class="field__label" for="rotationDegrees">Rotation</label>
          <select id="rotationDegrees" class="select" formControlName="rotationDegrees">
            @for (rotation of rotations; track rotation.value) {
              <option [value]="rotation.value">{{ rotation.label }}</option>
            }
          </select>
        </div>

        <h3 class="group field--full">Barcode ({{ settings().barcodeType }})</h3>
        <div class="field">
          <label class="field__label field__label--required" for="barcodeHeightMm">Barcode height (mm)</label>
          <input id="barcodeHeightMm" class="input numeric" type="number" step="0.5" formControlName="barcodeHeightMm" />
          <app-field-error [control]="form.controls.barcodeHeightMm" label="Barcode height" />
        </div>
        <div class="field">
          <label class="field__label field__label--required" for="barcodeModuleMm">Narrow bar (mm)</label>
          <input id="barcodeModuleMm" class="input numeric" type="number" step="0.025" formControlName="barcodeModuleMm" />
          <p class="field__hint">0.25 mm = one dot at 203 dpi. Raise it if the scanner struggles.</p>
          <app-field-error [control]="form.controls.barcodeModuleMm" label="Narrow bar" />
        </div>

        <h3 class="group field--full">Text</h3>
        <div class="field">
          <label class="field__label" for="serialFontPt">Serial number (pt)</label>
          <input id="serialFontPt" class="input numeric" type="number" step="0.5" formControlName="serialFontPt" />
        </div>
        <div class="field">
          <label class="field__label" for="purityFontPt">Purity (pt)</label>
          <input id="purityFontPt" class="input numeric" type="number" step="0.5" formControlName="purityFontPt" />
        </div>
        <div class="field">
          <label class="field__label" for="shopFontPt">Short name (pt)</label>
          <input id="shopFontPt" class="input numeric" type="number" step="0.5" formControlName="shopFontPt" />
        </div>
        <div class="field">
          <label class="field__label" for="detailFontPt">Name / weight / size (pt)</label>
          <input id="detailFontPt" class="input numeric" type="number" step="0.5" formControlName="detailFontPt" />
        </div>
        <div class="field">
          <label class="checkbox">
            <input type="checkbox" formControlName="showPurity" />
            <span>Print the purity (gold only)</span>
          </label>
        </div>

        @for (message of formErrors(); track message) {
          <div class="alert alert--error field--full">{{ message }}</div>
        }
      </form>

      <ng-container modalActions>
        <button type="button" class="btn btn--ghost" (click)="closed.emit()">Cancel</button>
        <button type="submit" form="labelSettingsForm" class="btn btn--primary" [disabled]="saving()">
          {{ saving() ? 'Saving...' : 'Save settings' }}
        </button>
      </ng-container>
    </app-modal>
  `,
  styles: [
    `
      .group {
        margin: var(--space-2) 0 0;
        font-size: var(--text-sm);
        text-transform: uppercase;
        letter-spacing: 0.08em;
        color: var(--text-muted);
        border-bottom: 1px solid var(--border-subtle);
        padding-bottom: var(--space-1);
      }
      .short { max-width: 8rem; letter-spacing: 0.1em; text-transform: uppercase; }
    `,
  ],
})
export class LabelSettingsComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(LabelsApiService);
  private readonly notifications = inject(NotificationService);

  readonly settings = input.required<LabelSettings>();
  readonly saved = output<LabelSettings>();
  readonly closed = output<void>();

  protected readonly rotations = LABEL_ROTATIONS;
  protected readonly saving = signal(false);
  protected readonly printers = signal<string[]>([]);
  protected readonly formErrors = signal<string[]>([]);

  protected readonly form = this.fb.nonNullable.group({
    shopShortName: ['', [Validators.required, Validators.maxLength(8)]],
    printerName: [''],
    printMode: ['DIRECT' as 'DIRECT' | 'AGENT', Validators.required],
    shopMark: ['LOGO' as 'LOGO' | 'TEXT' | 'NONE', Validators.required],
    shopLogoHeightMm: [6.5, [Validators.required, Validators.min(2), Validators.max(40)]],
    labelWidthMm: [50, [Validators.required, Validators.min(10), Validators.max(200)]],
    labelHeightMm: [25, [Validators.required, Validators.min(8), Validators.max(200)]],
    labelsAcross: [1, [Validators.required, Validators.min(1), Validators.max(10)]],
    contentWidthMm: [50, [Validators.required, Validators.min(5), Validators.max(400)]],
    contentHeightMm: [25, [Validators.required, Validators.min(2), Validators.max(200)]],
    marginTopMm: [2, [Validators.required, Validators.min(0)]],
    marginLeftMm: [2, [Validators.required, Validators.min(-200), Validators.max(200)]],
    offsetXMm: [0, [Validators.required, Validators.min(-20), Validators.max(20)]],
    offsetYMm: [0, [Validators.required, Validators.min(-20), Validators.max(20)]],
    rotationDegrees: [0, Validators.required],
    barcodeHeightMm: [10, [Validators.required, Validators.min(3), Validators.max(100)]],
    barcodeModuleMm: [0.25, [Validators.required, Validators.min(0.1), Validators.max(1)]],
    serialFontPt: [8, [Validators.required, Validators.min(3), Validators.max(30)]],
    purityFontPt: [7, [Validators.required, Validators.min(3), Validators.max(30)]],
    shopFontPt: [8, [Validators.required, Validators.min(3), Validators.max(30)]],
    detailFontPt: [6, [Validators.required, Validators.min(3), Validators.max(30)]],
    showPurity: [true],
  });

  ngOnInit(): void {
    this.api.printers().subscribe((list) => this.printers.set(list.printers));
    const current = this.settings();
    this.form.patchValue({
      shopShortName: current.shopShortName,
      printerName: current.printerName ?? '',
      printMode: current.printMode ?? 'DIRECT',
      shopMark: current.shopMark ?? 'LOGO',
      shopLogoHeightMm: Number(current.shopLogoHeightMm ?? 6.5),
      labelWidthMm: Number(current.labelWidthMm),
      labelHeightMm: Number(current.labelHeightMm),
      labelsAcross: Number(current.labelsAcross),
      contentWidthMm: Number(current.contentWidthMm),
      contentHeightMm: Number(current.contentHeightMm),
      marginTopMm: Number(current.marginTopMm),
      marginLeftMm: Number(current.marginLeftMm),
      offsetXMm: Number(current.offsetXMm),
      offsetYMm: Number(current.offsetYMm),
      rotationDegrees: Number(current.rotationDegrees),
      barcodeHeightMm: Number(current.barcodeHeightMm),
      barcodeModuleMm: Number(current.barcodeModuleMm),
      serialFontPt: Number(current.serialFontPt),
      purityFontPt: Number(current.purityFontPt),
      shopFontPt: Number(current.shopFontPt),
      detailFontPt: Number(current.detailFontPt),
      showPurity: current.showPurity,
    });
  }

  protected save(): void {
    clearServerErrors(this.form);
    this.formErrors.set([]);
    if (this.form.invalid) {
      markAllTouched(this.form);
      return;
    }
    const raw = this.form.getRawValue();
    this.saving.set(true);
    this.api
      .saveSettings(
        {
          ...raw,
          shopShortName: raw.shopShortName.trim(),
          printerName: raw.printerName?.trim() || null,
          rotationDegrees: Number(raw.rotationDegrees),
          labelsAcross: Number(raw.labelsAcross),
        },
        new HttpContext().set(SUPPRESS_ERROR_TOAST, true),
      )
      .subscribe({
        next: (settings) => {
          this.saving.set(false);
          this.notifications.success('Label settings saved.');
          this.saved.emit(settings);
        },
        error: (error) => {
          this.saving.set(false);
          this.formErrors.set(applyServerErrors(this.form, error));
        },
      });
  }
}
