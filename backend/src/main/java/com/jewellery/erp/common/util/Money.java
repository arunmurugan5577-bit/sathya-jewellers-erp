package com.jewellery.erp.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The rounding policy, in one place.
 *
 * <pre>
 *   weight   3 dp  HALF_UP   (milligram - what the scale reads)
 *   rate     2 dp  HALF_UP
 *   money    2 dp  HALF_UP   (stored scale of every NUMERIC(14,2) column)
 *   rupees   0 dp  HALF_UP   (billed line amounts and GST - the shop's receipts
 *                             carry no paise: Rs. 6,904.40 is billed as 6,904)
 * </pre>
 *
 * <p>Every calculator goes through these methods, so a change of policy - billing
 * in paise, say - is a change here rather than a hunt through the codebase.
 */
public final class Money {

    public static final int WEIGHT_SCALE = 3;
    public static final int MONEY_SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE);
    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {}

    public static BigDecimal weight(BigDecimal value) {
        return value.setScale(WEIGHT_SCALE, ROUNDING);
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, ROUNDING);
    }

    /** Rounds to whole rupees, but keeps the stored two-decimal scale. */
    public static BigDecimal rupees(BigDecimal value) {
        return value.setScale(0, ROUNDING).setScale(MONEY_SCALE);
    }

    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percentage) {
        return amount.multiply(percentage).divide(HUNDRED, 10, ROUNDING);
    }

    public static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
