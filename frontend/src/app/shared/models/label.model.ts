/*
 * Barcode label printing.
 *
 * The barcode value never appears here: the server reads the serial number from
 * the inventory row and encodes that, so the client has nothing to say about
 * what a tag carries.
 */

/** One label as the server would print it. */
export interface LabelLine {
  inventoryItemId: number;
  serialNumber: string;
  purityText?: string | null;
  itemTypeName: string;
  categoryName: string;
  subCategoryName?: string | null;
  shopShortName: string;
  status: 'AVAILABLE' | 'SOLD';
}

/** A selected piece that cannot be printed, and why. */
export interface LabelProblem {
  inventoryItemId?: number | null;
  serialNumber?: string | null;
  message: string;
}

export interface LabelPreview {
  labels: LabelLine[];
  problems: LabelProblem[];
  settings: LabelSettings;
}

/** Printer calibration. Lengths in millimetres, type sizes in points. */
/** Whether the labels are spooled by the server or by the shop's agent. */
export type LabelPrintMode = 'DIRECT' | 'AGENT';

/** What the shop's print agent last told the server. */
export interface LabelAgentStatus {
  online: boolean;
  lastSeenAt?: string | null;
  hostName?: string | null;
  agentVersion?: string | null;
  printers: string[];
  defaultPrinter?: string | null;
  pendingPages: number;
  failedPages: number;
}

export interface LabelSettings {
  shopShortName: string;
  /** DIRECT when the printer is on the server; AGENT when it is at the shop. */
  printMode: LabelPrintMode;
  /** What stands for the shop beside the barcode. */
  shopMark: 'LOGO' | 'TEXT' | 'NONE';
  /** Square box the logo is drawn in, in mm. */
  shopLogoHeightMm: number;
  labelWidthMm: number;
  labelHeightMm: number;
  /** Tags side by side across the roll; one printed page is one row. */
  labelsAcross: number;
  /** Printable length from the left edge - the head of a dumbbell tag. */
  contentWidthMm: number;
  /** Printable height at the top of each tag - the head of a dumbbell tag. */
  contentHeightMm: number;
  marginTopMm: number;
  marginLeftMm: number;
  offsetXMm: number;
  offsetYMm: number;
  rotationDegrees: number;
  barcodeHeightMm: number;
  barcodeModuleMm: number;
  serialFontPt: number;
  purityFontPt: number;
  shopFontPt: number;
  /** Type size of the name / weight / size block beside the barcode. */
  detailFontPt: number;
  /** Windows printer the labels go to; empty uses the machine default. */
  printerName?: string | null;
  showPurity: boolean;
  /** Fixed by the server: CODE128. */
  barcodeType: string;
}

export type LabelSettingsRequest = Omit<LabelSettings, 'barcodeType'>;

export interface LabelRequest {
  inventoryItemIds: number[];
  copies?: number;
  /** 1-based tag to start at, so a part-printed row can be used up. */
  startColumn?: number;
}

/** What a direct print did. */
export interface LabelPrintResult {
  /** True when the pages went to the shop agent rather than a local printer. */
  queued?: boolean;
  printed: number;
  printer: string;
}

/** Printers the server's machine can reach. */
export interface LabelPrinters {
  printers: string[];
  defaultPrinter?: string | null;
  configured?: string | null;
}

export const LABEL_ROTATIONS: readonly { value: number; label: string }[] = [
  { value: 0, label: '0° (normal)' },
  { value: 90, label: '90° (rotated right)' },
  { value: 180, label: '180° (upside down)' },
  { value: 270, label: '270° (rotated left)' },
];
