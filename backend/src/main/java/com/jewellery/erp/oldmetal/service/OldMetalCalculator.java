package com.jewellery.erp.oldmetal.service;

import com.jewellery.erp.common.util.Money;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Values old metal bought from a customer.
 *
 * <p>The shop buys as weighed: the customer is paid for everything on the
 * scale, not for the gold left after stones and solder are taken off. So the
 * gross weight is what the money is based on, and the net weight is recorded
 * beside it for what the metal is actually worth when it is melted.
 *
 * <pre>
 *   Gold bangle   gross 120.000 g   x   rate 100   =   12,000
 *   Gold coin     net 8.000 g       x   rate 14,370   =   1,14,960
 * </pre>
 *
 * <p>A coin has nothing to deduct, so its gross weight is left blank and the
 * net weight stands in - the two are the same number and asking for it twice
 * would only invite a typing mistake.
 *
 * <p>Rounded to whole rupees, as the blank paise column on the shop's paper
 * bill shows. The rate is the one quoted for that metal and purity on the day;
 * purity is recorded, not multiplied in. If the shop ever values old metal by
 * fineness or deducts melting loss, this is the one class to change.
 */
@Component
public class OldMetalCalculator {

    /**
     * @param grossWeightGrams the weight on the scale; null for a piece with
     *     nothing to deduct, where the net weight is the whole of it
     * @param netWeightGrams the weight after any deduction
     */
    public BigDecimal lineAmount(
            BigDecimal grossWeightGrams, BigDecimal netWeightGrams, BigDecimal ratePerGram) {
        return Money.rupees(Money.weight(payableWeight(grossWeightGrams, netWeightGrams)).multiply(ratePerGram));
    }

    /** The weight the customer is paid for. */
    public BigDecimal payableWeight(BigDecimal grossWeightGrams, BigDecimal netWeightGrams) {
        return grossWeightGrams == null ? netWeightGrams : grossWeightGrams;
    }
}
