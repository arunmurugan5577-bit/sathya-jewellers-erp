/*
 * Customers, old gold / silver purchases, sales and reports.
 *
 * Money and weights arrive from the server as JSON numbers produced from exact
 * decimals. The client only ever DISPLAYS them - every figure on an invoice is
 * computed by the server - so no arithmetic here can drift from the ledger.
 */

// --- Customers ---------------------------------------------------------------

export interface Customer {
  id: number;
  customerCode: string;
  fullName: string;
  mobileNumber?: string | null;
  email?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  city?: string | null;
  state?: string | null;
  pincode?: string | null;
  gstin?: string | null;
  pan?: string | null;
  active: boolean;
  createdAt?: string;
  createdBy?: string;
}

export interface CustomerSummary {
  id: number;
  customerCode: string;
  fullName: string;
  mobileNumber?: string | null;
  formattedAddress?: string | null;
}

export interface CustomerRequest {
  fullName: string;
  mobileNumber?: string | null;
  email?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  city?: string | null;
  state?: string | null;
  pincode?: string | null;
  gstin?: string | null;
  pan?: string | null;
}

// --- Old gold / silver ------------------------------------------------------------

export type OldMetalStatus = 'AVAILABLE' | 'PARTIALLY_USED' | 'USED' | 'CANCELLED';

export interface OldMetalItem {
  lineNumber: number;
  itemTypeId: number;
  itemTypeName: string;
  purityId?: number | null;
  purityName?: string | null;
  particulars: string;
  hsnCode: string;
  netWeightGrams: number;
  grossWeightGrams?: number | null;
  ratePerGram: number;
  amount: number;
}

export interface OldMetalTransaction {
  id: number;
  transactionNumber: string;
  transactionDate: string;
  customerId: number;
  customerName: string;
  customerMobile?: string | null;
  customerAddress?: string | null;
  sellerName: string;
  sellerAddress?: string | null;
  sellerMobile?: string | null;
  sellerGstin?: string | null;
  totalAmount: number;
  usedAmount: number;
  availableAmount: number;
  status: OldMetalStatus;
  amountInWords: string;
  remarks?: string | null;
  cancelReason?: string | null;
  cancelledAt?: string | null;
  cancelledBy?: string | null;
  items: OldMetalItem[];
  createdAt: string;
  createdBy?: string | null;
}

export interface OldMetalSummary {
  id: number;
  transactionNumber: string;
  transactionDate: string;
  customerId: number;
  customerName: string;
  customerMobile?: string | null;
  totalAmount: number;
  usedAmount: number;
  availableAmount: number;
  status: OldMetalStatus;
}

export interface OldMetalOption {
  id: number;
  transactionNumber: string;
  transactionDate: string;
  description: string;
  totalAmount: number;
  usedAmount: number;
  availableAmount: number;
  status: OldMetalStatus;
}

export interface OldMetalItemRequest {
  itemTypeId: number | null;
  purityId?: number | null;
  particulars: string;
  hsnCode: string;
  netWeightGrams: number | null;
  grossWeightGrams?: number | null;
  ratePerGram: number | null;
}

export interface OldMetalRequest {
  customerId: number | null;
  transactionDate?: string | null;
  items: OldMetalItemRequest[];
  remarks?: string | null;
}

// --- Sales -------------------------------------------------------------------------

export type SaleStatus = 'COMPLETED' | 'CANCELLED';
export type PaymentStatus = 'UNPAID' | 'PARTIAL' | 'PAID';
export type PaymentMethod = 'CASH' | 'CARD' | 'UPI' | 'BANK_TRANSFER';

export const PAYMENT_METHODS: readonly { value: PaymentMethod; label: string }[] = [
  { value: 'CASH', label: 'Cash' },
  { value: 'UPI', label: 'UPI' },
  { value: 'CARD', label: 'Card' },
  { value: 'BANK_TRANSFER', label: 'Bank transfer' },
];

/** What the counter sees the moment a serial number is entered. */
export interface SaleItemLookup {
  inventoryItemId: number;
  serialNumber: string;
  itemTypeName: string;
  purityName: string;
  categoryId: number;
  categoryName: string;
  subCategoryId?: number | null;
  subCategoryName?: string | null;
  hsnCode: string;
  gstPercentage: number;
  netWeightGrams: number;
  size?: string | null;
  description?: string | null;
  suggestedParticulars: string;
}

export interface SaleLine {
  lineNumber: number;
  inventoryItemId: number;
  serialNumber: string;
  particulars: string;
  itemTypeName: string;
  purityName: string;
  categoryName: string;
  subCategoryName?: string | null;
  hsnCode: string;
  gstPercentage: number;
  netWeightGrams: number;
  wastagePercentage: number;
  wastageWeightGrams: number;
  grossWeightGrams: number;
  ratePerGram: number;
  makingCharge: number;
  amount: number;
  cgstAmount: number;
  sgstAmount: number;
  discountAmount: number;
  lineStatus: 'ACTIVE' | 'CANCELLED';
}

export interface SaleTotals {
  subtotal: number;
  cgstAmount: number;
  sgstAmount: number;
  taxAmount: number;
  discountAmount: number;
  grandTotal: number;
  oldMetalAdjustmentAmount: number;
  roundOffAmount: number;
  netPayable: number;
  amountPaid: number;
  balanceAmount: number;
  paymentStatus: PaymentStatus;
  grandTotalInWords: string;
  netPayableInWords: string;
}

export interface SaleCalculation {
  lines: SaleLine[];
  totals: SaleTotals;
}

export interface SaleAdjustment {
  id: number;
  oldMetalTransactionId: number;
  transactionNumber: string;
  transactionDate: string;
  adjustmentAmount: number;
  status: 'ACTIVE' | 'REVERSED';
}

export interface SalePayment {
  id: number;
  method: PaymentMethod;
  amount: number;
  referenceNumber?: string | null;
  paymentDate: string;
  remarks?: string | null;
  createdAt: string;
  createdBy?: string | null;
}

export interface Sale {
  id: number;
  invoiceNumber: string;
  invoiceDate: string;
  status: SaleStatus;
  customerId: number;
  customerCode: string;
  customerName: string;
  customerMobile?: string | null;
  customerAddress?: string | null;
  customerGstin?: string | null;
  sellerName: string;
  sellerAddress?: string | null;
  sellerMobile?: string | null;
  sellerGstin?: string | null;
  items: SaleLine[];
  totals: SaleTotals;
  oldMetalAdjustments: SaleAdjustment[];
  payments: SalePayment[];
  remarks?: string | null;
  cancelReason?: string | null;
  cancelledAt?: string | null;
  cancelledBy?: string | null;
  createdAt: string;
  createdBy?: string | null;
}

export interface SaleSummary {
  id: number;
  invoiceNumber: string;
  invoiceDate: string;
  status: SaleStatus;
  customerId: number;
  customerName: string;
  customerMobile?: string | null;
  grandTotal: number;
  netPayable: number;
  amountPaid: number;
  balanceAmount: number;
  paymentStatus: PaymentStatus;
}

export interface SaleItemRequest {
  serialNumber: string;
  particulars?: string | null;
  wastagePercentage?: number | null;
  ratePerGram: number | null;
  makingCharge?: number | null;
}

export interface SalePaymentRequest {
  method: PaymentMethod;
  amount: number | null;
  referenceNumber?: string | null;
  remarks?: string | null;
}

export interface SaleRequest {
  customerId: number | null;
  invoiceDate?: string | null;
  items: SaleItemRequest[];
  discountAmount?: number | null;
  oldMetalAdjustments?: { transactionId: number; amount: number | null }[];
  payments?: SalePaymentRequest[];
  remarks?: string | null;
}

// --- Reports -----------------------------------------------------------------------

export type ReportColumnType = 'TEXT' | 'DATE' | 'DATETIME' | 'INTEGER' | 'WEIGHT' | 'MONEY' | 'PERCENT';

export interface ReportColumn {
  key: string;
  header: string;
  type: ReportColumnType;
  total: boolean;
}

export interface ReportSheetPreview {
  name: string;
  columns: ReportColumn[];
  rows: Record<string, string | number | null>[];
  rowCount: number;
  truncated: boolean;
  totals: Record<string, number>;
}

export interface ReportPreview {
  title: string;
  startDate: string;
  endDate: string;
  filters: string[];
  sheets: ReportSheetPreview[];
}
