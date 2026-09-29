package com.jewellery.erp.common.exception;

/**
 * Stable, machine-readable failure codes returned in {@code ApiErrorResponse.code}.
 *
 * <p>The human message may be reworded at any time; these may not. A client that
 * needs to react to a specific failure - highlight the serial number field when an
 * item is already sold, say - should branch on the code, never on the text.
 */
public enum ErrorCode {
    VALIDATION_FAILED,
    UNAUTHORIZED,
    FORBIDDEN,
    RESOURCE_NOT_FOUND,
    DUPLICATE_RESOURCE,
    INVALID_DATE_RANGE,

    CUSTOMER_NOT_FOUND,
    CUSTOMER_INACTIVE,

    INVENTORY_ITEM_NOT_FOUND,
    INVENTORY_ITEM_INACTIVE,
    INVENTORY_ITEM_NOT_BILLABLE,
    ITEM_ALREADY_SOLD,

    OLD_METAL_NOT_FOUND,
    OLD_METAL_ALREADY_USED,
    OLD_METAL_AMOUNT_EXCEEDED,
    OLD_METAL_CUSTOMER_MISMATCH,
    OLD_METAL_INVALID_ITEM_TYPE,

    INVOICE_NOT_FOUND,
    SALE_ALREADY_CANCELLED,
    PAYMENT_EXCEEDS_BALANCE,
    DISCOUNT_EXCEEDS_TOTAL,

    DOCUMENT_NUMBER_TOO_LONG
}
