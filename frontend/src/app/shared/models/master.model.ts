import { Audited } from './audit.model';

/** Shared shape of the "name + code" masters. */
export interface NamedMaster extends Audited {
  id: number;
  name: string;
  code: string;
  description?: string | null;
  active: boolean;
}

export interface NamedMasterRequest {
  name: string;
  code: string;
  description?: string | null;
  active?: boolean;
}

export interface ItemType extends NamedMaster {
  purityCount: number;
}

export interface Category extends NamedMaster {
  subCategoryCount: number;
}

export interface SubCategory extends NamedMaster {
  categoryId: number;
  categoryName: string;
}

export interface SubCategoryRequest extends NamedMasterRequest {
  categoryId: number | null;
}

export interface HsnCode extends Audited {
  id: number;
  hsnCode: string;
  description?: string | null;
  gstPercentage: number;
  active: boolean;
}

export interface HsnCodeRequest {
  hsnCode: string;
  description?: string | null;
  gstPercentage: number | null;
  active?: boolean;
}

export interface Purity extends Audited {
  id: number;
  itemTypeId: number;
  itemTypeName: string;
  name: string;
  purityValue: number;
  description?: string | null;
  active: boolean;
}

export interface PurityRequest {
  itemTypeId: number | null;
  name: string;
  purityValue: number | null;
  description?: string | null;
  active?: boolean;
}
