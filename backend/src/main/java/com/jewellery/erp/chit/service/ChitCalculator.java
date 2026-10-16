package com.jewellery.erp.chit.service;

import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.ErrorCode;
import com.jewellery.erp.common.util.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * The chit arithmetic. The single source of truth for every figure on a chit;
 * the screens show what the server works out and never repeat these formulas.
 *
 * <p>The specification is the shop's own chit sheet:
 *
 * <pre>
 *   500 a month   x 11 paid + 1 bonus  ->   6,000
 *   1,000         x 11 + 1             ->  12,000
 *   2,000         x 11 + 1             ->  24,000
 *   3,000         x 11 + 1             ->  36,000, bought as gold month by month
 *   5,000         x 11 + 1             ->  60,000
 *   10,000        x 11 + 1             -> 1,20,000
 *
 *   at a rate of 13,675 a gram:
 *     3,000  -> 0.219 g       36,000  -> 2.632 g
 *     5,000  -> 0.365 g       60,000  -> 4.387 g
 *    10,000  -> 0.731 g     1,20,000  -> 8.775 g
 * </pre>
 *
 * <p>Those gram figures are where the one surprise lives, and it is deliberate:
 * the shop <em>rounds grams down</em>. 5,000 at 13,675 is 0.365631 g, which the
 * sheet writes as 0.365 and not the 0.366 that normal rounding gives; the same
 * happens at 60,000 and at 36,000. So {@link #gramsFor} floors, where
 * {@link Money#weight} elsewhere in this application rounds half up.
 *
 * <p>Two reasons to keep it that way rather than tidy it into line. A customer's
 * passbook has to agree with what the owner writes by hand, or every chit ends
 * in an argument over a milligram. And rounding down never credits a customer
 * with gold the shop did not buy, where rounding up sometimes would. The cost
 * is at most a milligram an instalment - under 12 mg over a whole chit.
 */
@Component
public class ChitCalculator {

    /**
     * Grams bought by one instalment.
     *
     * <p>Floored to the milligram: see the class note. The scale a jeweller's
     * weight carries everywhere else in the application is the same 3 dp, so
     * only the direction differs.
     */
    public BigDecimal gramsFor(BigDecimal amount, BigDecimal ratePerGram) {
        requirePositive(amount, "amount", "The instalment amount must be more than zero.");
        requirePositive(ratePerGram, "ratePerGram", "The gold rate must be more than zero.");
        return amount.divide(ratePerGram, Money.WEIGHT_SCALE, RoundingMode.DOWN);
    }

    /**
     * What a chit is worth when it is taken as money.
     *
     * <p>A money chit is worth what was put into it. A gold chit is worth what
     * its grams fetch on the day it is cashed, which is the point of saving in
     * gold: the customer carries the price, up or down.
     */
    public BigDecimal cashValue(BigDecimal balanceAmount, BigDecimal balanceGrams, BigDecimal ratePerGram) {
        if (balanceGrams == null || balanceGrams.signum() == 0) {
            return Money.money(Money.nullToZero(balanceAmount));
        }
        requirePositive(ratePerGram, "ratePerGram", "Enter the gold rate to value the chit.");
        return Money.rupees(balanceGrams.multiply(ratePerGram));
    }

    /**
     * What the whole chit comes to if every instalment is paid.
     *
     * <p>Twelve months of value for eleven months of money - the shop puts in
     * the last one. Shown when a chit is opened, so the customer is told the
     * figure before they join.
     */
    public BigDecimal maturityAmount(BigDecimal monthlyAmount, short totalInstalments) {
        requirePositive(monthlyAmount, "monthlyAmount", "The monthly amount must be more than zero.");
        return Money.money(monthlyAmount.multiply(BigDecimal.valueOf(totalInstalments)));
    }

    /** What the customer still owes to finish the chit. The bonus month is not theirs to pay. */
    public BigDecimal remainingToPay(
            BigDecimal monthlyAmount, short payableInstalments, short paidInstalments) {
        int left = Math.max(0, payableInstalments - paidInstalments);
        return Money.money(monthlyAmount.multiply(BigDecimal.valueOf(left)));
    }

    private static void requirePositive(BigDecimal value, String field, String message) {
        if (value == null || value.signum() <= 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, field, message);
        }
    }
}
