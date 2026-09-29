package com.jewellery.erp.sales.entity;

/** Lifecycle of an invoice. Cancelled invoices are kept, never deleted. */
public enum SaleStatus {
    COMPLETED,
    CANCELLED
}
