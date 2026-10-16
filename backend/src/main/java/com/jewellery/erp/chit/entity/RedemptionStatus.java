package com.jewellery.erp.chit.entity;

/**
 * Whether a redemption still stands.
 *
 * <p>Cancelling the invoice a chit was spent against puts the value back on
 * the chit and marks the redemption CANCELLED rather than erasing it, so the
 * chit's history still shows that it was spent and then handed back.
 */
public enum RedemptionStatus {
    ACTIVE,
    CANCELLED
}
