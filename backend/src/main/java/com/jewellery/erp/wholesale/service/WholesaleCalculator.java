package com.jewellery.erp.wholesale.service;

import com.jewellery.erp.common.util.Money;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Values a wholesale estimate.
 *
 * <p>Wholesale is not priced like the counter. A retail invoice is arithmetic
 * in rupees; an estimate is arithmetic in <em>pure gold</em>, and the rupees
 * fall out of it at the day's pure rate. The shop's own estimate No. 3 is the
 * specification:
 *
 * <pre>
 *   G Pure Rate  14,960 / g
 *   G.CHAIN 916  jewel 16.060 g  x  touch 98%  =  pure 15.739 g
 *                15.739 x 14,960 + MC 160      =  2,35,615
 *
 *   opening   3.373 g pure, Rs 0     ->  Rs   50,460
 *   closing  19.112 g pure, Rs 160   ->  Rs 2,86,076
 * </pre>
 *
 * <p>The party's account runs in two currencies at once: grams of pure gold,
 * and a rupee balance for making and stone charges. They are kept apart because
 * only the gold is revalued when the rate moves - yesterday's making charge is
 * still yesterday's rupees.
 */
@Component
public class WholesaleCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * Pure gold in a piece: its weight at its touch.
     *
     * @param jewelWeightGrams as weighed
     * @param purePercentage the touch, out of 100
     */
    public BigDecimal pureWeight(BigDecimal jewelWeightGrams, BigDecimal purePercentage) {
        return Money.weight(jewelWeightGrams.multiply(purePercentage).divide(HUNDRED, 6, java.math.RoundingMode.HALF_UP));
    }

    /**
     * What the line comes to.
     *
     * <p>The gold is valued at the rate, then the making and stone charges are
     * added as they stand - they are rupees already and the rate does not touch
     * them. Rounded once, at the end, to whole rupees as the paper bill is.
     */
    public BigDecimal itemAmount(
            BigDecimal pureWeightGrams,
            BigDecimal ratePerGram,
            BigDecimal makingCharge,
            BigDecimal stoneAmount) {
        return Money.rupees(pureWeightGrams.multiply(ratePerGram)
                .add(zeroIfNull(makingCharge))
                .add(zeroIfNull(stoneAmount)));
    }

    /** Making plus stone: the part of the line that is rupees rather than gold. */
    public BigDecimal miscAmount(BigDecimal makingCharge, BigDecimal stoneAmount) {
        return Money.money(zeroIfNull(makingCharge).add(zeroIfNull(stoneAmount)));
    }

    /**
     * A balance in rupees: its gold at today's rate, plus its rupee part.
     *
     * <p>This is what the slip prints as "Op Bal Value" and "Cl Bal Value".
     */
    public BigDecimal balanceValue(BigDecimal pureGrams, BigDecimal miscAmount, BigDecimal pureRatePerGram) {
        return Money.rupees(pureGrams.multiply(pureRatePerGram).add(zeroIfNull(miscAmount)));
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
