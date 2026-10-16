package com.jewellery.erp.chit.entity;

/** The two ways a matured chit leaves the shop. */
public enum RedemptionKind {

    /** The customer took the money. A gold chit is sold at the rate on the day. */
    CASH,

    /** The customer bought jewellery, and the chit came off the invoice. */
    SALE
}
