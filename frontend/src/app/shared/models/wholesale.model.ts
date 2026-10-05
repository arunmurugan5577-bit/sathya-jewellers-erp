/**
 * Wholesale estimates.
 *
 * <p>Wholesale is priced in pure gold rather than rupees: a piece's weight at
 * its touch gives a pure weight, the pure weight at the day's rate gives the
 * money, and the party's account carries both a gram balance and a rupee one.
 */

export type WholesaleStatus = 'COMPLETED' | 'CANCELLED';
export type WholesaleLineStatus = 'ACTIVE' | 'CANCELLED';

/** A piece in stock, priced for the estimate form. */
export interface WholesaleItemLookup {
  inventoryItemId: number;
  serialNumber: string;
  jewelName: string;
  itemTypeName: string;
  purityName: string;
  jewelWeightGrams: number;
  /** Derived from the purity master; the counter overwrites it. */
  suggestedPurePercentage?: number | null;
}

export interface WholesaleLine {
  lineNumber: number;
  inventoryItemId: number;
  serialNumber: string;
  jewelName: string;
  jewelWeightGrams: number;
  purePercentage: number;
  pureWeightGrams: number;
  ratePerGram: number;
  makingCharge: number;
  stoneAmount: number;
  itemAmount: number;
  lineStatus: WholesaleLineStatus;
}

/** Everything the printed estimate shows below the lines. */
export interface WholesaleTotals {
  openingPureGrams: number;
  openingMiscAmount: number;
  openingValue: number;
  totalPureGrams: number;
  totalMiscAmount: number;
  totalAmount: number;
  closingPureGrams: number;
  closingMiscAmount: number;
  closingValue: number;
  totalAmountInWords: string;
}

export interface WholesaleCalculation {
  lines: WholesaleLine[];
  totals: WholesaleTotals;
}

export interface WholesaleEstimate {
  id: number;
  estimateNumber: string;
  estimateDate: string;
  status: WholesaleStatus;
  customerId: number;
  customerCode: string;
  customerName: string;
  customerMobile?: string | null;
  sellerName: string;
  sellerAddress: string;
  sellerMobile?: string | null;
  pureRatePerGram: number;
  items: WholesaleLine[];
  totals: WholesaleTotals;
  remarks?: string | null;
  cancelReason?: string | null;
  cancelledAt?: string | null;
  cancelledBy?: string | null;
  createdAt: string;
  createdBy?: string | null;
}

export interface WholesaleSummary {
  id: number;
  estimateNumber: string;
  estimateDate: string;
  customerName: string;
  totalPureGrams: number;
  totalAmount: number;
  closingPureGrams: number;
  closingValue: number;
  status: WholesaleStatus;
}

/** A party's running account. */
export interface WholesaleBalance {
  customerId: number;
  customerName: string;
  pureGrams: number;
  miscAmount: number;
  valueAtRate?: number | null;
}

export interface WholesaleItemRequest {
  serialNumber: string;
  jewelName?: string | null;
  purePercentage: number | null;
  ratePerGram?: number | null;
  makingCharge?: number | null;
  stoneAmount?: number | null;
}

export interface WholesaleRequest {
  customerId: number | null;
  estimateDate: string | null;
  pureRatePerGram: number | null;
  items: WholesaleItemRequest[];
  remarks?: string | null;
}
