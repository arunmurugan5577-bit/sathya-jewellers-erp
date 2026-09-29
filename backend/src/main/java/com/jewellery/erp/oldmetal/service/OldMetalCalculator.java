package com.jewellery.erp.oldmetal.service;

import com.jewellery.erp.common.util.Money;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Values old metal bought from a customer.
 *
 * <p>The shop's own purchase bill (No. 74) is the specification:
 *
 * <pre>
 *   Gold coin   net 8.000 g   x   rate 14,370   =   1,14,960
 * </pre>
 *
 * <p>So: {@code amount = round(net weight x rate)} to whole rupees, as the bill's
 * blank paise column shows. The rate is the one quoted for that metal and purity
 * on the day; purity is recorded, not multiplied in. If the shop ever values old
 * metal by fineness or deducts melting loss, this is the one class to change.
 */
@Component
public class OldMetalCalculator {

    public BigDecimal lineAmount(BigDecimal netWeightGrams, BigDecimal ratePerGram) {
        return Money.rupees(Money.weight(netWeightGrams).multiply(ratePerGram));
    }
}
