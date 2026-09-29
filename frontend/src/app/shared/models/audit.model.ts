/** Audit columns present on every business record. */
export interface Audited {
  createdAt?: string;
  createdBy?: string | null;
  updatedAt?: string;
  updatedBy?: string | null;
}
