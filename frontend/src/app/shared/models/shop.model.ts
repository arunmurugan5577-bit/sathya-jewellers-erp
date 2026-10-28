import { Audited } from './audit.model';

export interface ShopSettings extends Audited {
  id: number;
  shopName: string;
  addressLine1?: string | null;
  addressLine2?: string | null;
  city?: string | null;
  state?: string | null;
  pincode?: string | null;
  mobileNumber?: string | null;
  alternateMobileNumber?: string | null;
  email?: string | null;
  gstin?: string | null;
}

export type ShopSettingsRequest = Omit<ShopSettings, 'id' | keyof Audited>;

/** Dashboard counters. A missing field means the user may not see that metric. */
/** What the shop holds under one sub category. */
export interface StockBySubCategory {
  /** Null for pieces filed under no sub category. */
  subCategoryId?: number | null;
  subCategoryName: string;
  items: number;
  weightGrams: number;
}

/** What the shop holds under one category, and the sub categories under it. */
export interface StockByCategory {
  categoryId: number;
  categoryName: string;
  items: number;
  weightGrams: number;
  subCategories: StockBySubCategory[];
}

export interface DashboardSummary {
  totalItems?: number;
  activeItems?: number;
  activeWeightGrams?: number;
  totalCategories?: number;
  totalUsers?: number;
  activeUsers?: number;
}
