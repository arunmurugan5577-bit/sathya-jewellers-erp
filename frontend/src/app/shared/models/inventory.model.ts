import { Audited } from './audit.model';

/** One physical jewellery piece. */
export interface InventoryItem extends Audited {
  id: number;
  serialNumber: string;
  itemTypeId: number;
  itemTypeName: string;
  purityId: number;
  purityName: string;
  purityValue: number;
  categoryId: number;
  categoryName: string;
  subCategoryId?: number | null;
  subCategoryName?: string | null;
  hsnId?: number | null;
  hsnCode?: string | null;
  gstPercentage?: number | null;
  size?: string | null;
  weightGrams: number;
  description?: string | null;
  active: boolean;
  /** AVAILABLE until invoiced, then SOLD. Set only by sales. */
  status: InventoryStatus;
}

export type InventoryStatus = 'AVAILABLE' | 'SOLD';

export interface InventoryItemRequest {
  serialNumber: string;
  itemTypeId: number | null;
  purityId: number | null;
  categoryId: number | null;
  subCategoryId?: number | null;
  hsnId?: number | null;
  size?: string | null;
  weightGrams: number | null;
  description?: string | null;
  active?: boolean;
}

/** Filters accepted by the inventory list endpoint. */
export interface InventoryFilters {
  search?: string | null;
  serialNumber?: string | null;
  itemTypeId?: number | null;
  purityId?: number | null;
  categoryId?: number | null;
  subCategoryId?: number | null;
  active?: boolean | null;
  status?: InventoryStatus | null;
}
