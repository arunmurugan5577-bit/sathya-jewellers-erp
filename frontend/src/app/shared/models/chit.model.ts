/**
 * Chits - the monthly savings the shop runs.
 *
 * <p>A customer pays eleven months and the shop puts in the twelfth. On a
 * MONEY scheme the balance is rupees; on a GOLD scheme each instalment buys
 * grams at that day's rate, so what the customer ends up with is a weight, not
 * an amount, and a rising rate buys less each month.
 */

import { PaymentMethod } from './sales.model';

export type ChitSchemeType = 'MONEY' | 'GOLD';
export type ChitStatus = 'ACTIVE' | 'COMPLETE' | 'MATURED' | 'CLOSED' | 'CANCELLED';

export interface ChitScheme {
  id: number;
  code: string;
  name: string;
  schemeType: ChitSchemeType;
  monthlyAmount: number;
  /** What the customer pays: eleven. */
  payableInstalments: number;
  /** What the chit is worth: twelve. */
  totalInstalments: number;
  maturityAmount: number;
  active: boolean;
}

/** One month on the passbook. The twelfth is the shop's. */
export interface ChitPayment {
  id: number;
  instalmentNo: number;
  paidOn: string;
  amount: number;
  ratePerGram?: number | null;
  grams?: number | null;
  paidByShop: boolean;
  paymentMethod?: PaymentMethod | null;
  referenceNo?: string | null;
  remarks?: string | null;
  createdAt: string;
  createdBy: string;
}

export type RedemptionKind = 'CASH' | 'SALE';

/** What became of a chit: taken as cash, or spent against an invoice. */
export interface ChitRedemption {
  id: number;
  redeemedOn: string;
  kind: RedemptionKind;
  grams?: number | null;
  ratePerGram?: number | null;
  amount: number;
  saleId?: number | null;
  invoiceNumber?: string | null;
  status: 'ACTIVE' | 'CANCELLED';
  paymentMethod?: PaymentMethod | null;
  referenceNo?: string | null;
  remarks?: string | null;
  createdAt: string;
  createdBy: string;
}

export interface ChitAccount {
  id: number;
  chitNumber: string;
  customerId: number;
  customerCode: string;
  customerName: string;
  customerMobile?: string | null;
  schemeId: number;
  schemeCode: string;
  schemeName: string;
  schemeType: ChitSchemeType;
  monthlyAmount: number;
  payableInstalments: number;
  totalInstalments: number;
  startedOn: string;
  status: ChitStatus;
  paidInstalments: number;
  /** Months the customer still owes. The bonus is not theirs to pay. */
  instalmentsOwed: number;
  paidAmount: number;
  accruedGrams: number;
  balanceAmount: number;
  balanceGrams: number;
  maturityAmount: number;
  remainingToPay: number;
  maturedOn?: string | null;
  closedOn?: string | null;
  closeReason?: string | null;
  remarks?: string | null;
  payments: ChitPayment[];
  redemptions: ChitRedemption[];
  createdAt: string;
  createdBy: string;
}

export interface ChitSummary {
  id: number;
  chitNumber: string;
  customerName: string;
  customerMobile?: string | null;
  schemeName: string;
  schemeType: ChitSchemeType;
  monthlyAmount: number;
  startedOn: string;
  status: ChitStatus;
  paidInstalments: number;
  payableInstalments: number;
  paidAmount: number;
  accruedGrams: number;
  balanceAmount: number;
  balanceGrams: number;
}

export interface OpenChitRequest {
  customerId: number;
  schemeId: number;
  startedOn?: string | null;
  remarks?: string | null;
}

/** The rate belongs on a gold chit only; the server refuses it on a money one. */
export interface ChitPaymentRequest {
  paidOn?: string | null;
  ratePerGram?: number | null;
  paymentMethod?: PaymentMethod | null;
  referenceNo?: string | null;
  remarks?: string | null;
}

/** Taking the chit as cash. The whole balance goes and the chit closes. */
export interface WithdrawChitRequest {
  withdrawnOn?: string | null;
  ratePerGram?: number | null;
  paymentMethod?: PaymentMethod | null;
  referenceNo?: string | null;
  remarks?: string | null;
}

export interface MatureChitRequest {
  maturedOn?: string | null;
  ratePerGram?: number | null;
}

/** What a status means at the counter, for the badge and the empty states. */
export const CHIT_STATUS_LABELS: Readonly<Record<ChitStatus, string>> = {
  ACTIVE: 'Running',
  COMPLETE: 'Awaiting bonus',
  MATURED: 'Matured',
  CLOSED: 'Closed',
  CANCELLED: 'Cancelled',
};
