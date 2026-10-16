package com.jewellery.erp.chit.entity;

/**
 * Where a chit has got to.
 *
 * <pre>
 *   ACTIVE    -> COMPLETE -> MATURED -> CLOSED
 *      \___________\___________/
 *                  CANCELLED
 * </pre>
 */
public enum ChitStatus {

    /** Still being paid into. */
    ACTIVE,

    /**
     * Every instalment the customer owes is in, and the shop has not yet put
     * in the bonus.
     *
     * <p>A step of its own rather than maturing on the last payment, because a
     * gold chit's bonus has to buy grams at a rate somebody types in, and the
     * counter should not be asked for it in the middle of taking a payment.
     */
    COMPLETE,

    /** The bonus is in. The balance is the customer's to spend or withdraw. */
    MATURED,

    /** Spent or withdrawn, in full. */
    CLOSED,

    /** Abandoned before maturity. What was paid in is settled outside the system. */
    CANCELLED
}
