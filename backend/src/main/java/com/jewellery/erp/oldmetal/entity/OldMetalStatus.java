package com.jewellery.erp.oldmetal.entity;

import java.util.Set;

/**
 * Where a purchase bill's value stands.
 *
 * <pre>
 *   AVAILABLE -> PARTIALLY_USED -> USED
 *       |
 *       +-> CANCELLED   (only while nothing has been used)
 * </pre>
 *
 * Mirrors {@code ck_old_metal_status_matches_usage}, which enforces the same
 * transitions in the database.
 */
public enum OldMetalStatus {
    AVAILABLE,
    PARTIALLY_USED,
    USED,
    CANCELLED;

    /** States whose remaining value can still be applied to a sale. */
    public static final Set<OldMetalStatus> USABLE = Set.of(AVAILABLE, PARTIALLY_USED);
}
