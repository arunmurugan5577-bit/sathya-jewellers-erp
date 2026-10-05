package com.jewellery.erp.wholesale;

import static org.assertj.core.api.Assertions.assertThat;

import com.jewellery.erp.wholesale.service.WholesaleCalculator;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The shop's wholesale estimate No. 3 is the specification. Every printed
 * figure on it has to come back out of this class.
 */
class WholesaleCalculatorTest {

    private final WholesaleCalculator calculator = new WholesaleCalculator();

    private static final BigDecimal PURE_RATE = new BigDecimal("14960");

    @Test
    @DisplayName("estimate No. 3 reproduces line for line")
    void reproducesTheShopsEstimate() {
        // G.CHAIN 916 ROUND, 16.060 g at 98 touch
        BigDecimal pure = calculator.pureWeight(new BigDecimal("16.060"), new BigDecimal("98.00"));
        assertThat(pure).isEqualByComparingTo("15.739");

        BigDecimal amount = calculator.itemAmount(pure, PURE_RATE, new BigDecimal("160"), BigDecimal.ZERO);
        assertThat(amount).isEqualByComparingTo("235615");
    }

    @Test
    @DisplayName("the opening and closing balances reproduce too")
    void reproducesTheRunningAccount() {
        BigDecimal openingPure = new BigDecimal("3.373");
        assertThat(calculator.balanceValue(openingPure, BigDecimal.ZERO, PURE_RATE))
                .isEqualByComparingTo("50460");

        BigDecimal closingPure = openingPure.add(new BigDecimal("15.739"));
        assertThat(closingPure).isEqualByComparingTo("19.112");
        assertThat(calculator.balanceValue(closingPure, new BigDecimal("160"), PURE_RATE))
                .isEqualByComparingTo("286076");
    }

    @Test
    @DisplayName("making and stone charges are rupees, not gold")
    void miscIsMakingPlusStone() {
        assertThat(calculator.miscAmount(new BigDecimal("160"), new BigDecimal("60")))
                .isEqualByComparingTo("220");
        assertThat(calculator.miscAmount(new BigDecimal("160"), null)).isEqualByComparingTo("160");
        assertThat(calculator.miscAmount(null, null)).isEqualByComparingTo("0");

        // A stone amount lands in the total as plain rupees - the rate does not
        // touch it, because a stone is not gold.
        BigDecimal pure = new BigDecimal("15.739");
        BigDecimal withStone = calculator.itemAmount(pure, PURE_RATE, new BigDecimal("160"), new BigDecimal("60"));
        assertThat(withStone).isEqualByComparingTo("235675");
    }

    @Test
    @DisplayName("pure weight is kept to the milligram")
    void pureWeightIsThreeDecimals() {
        // 16.060 x 98% = 15.7388, which is 15.739 g on the bill.
        assertThat(calculator.pureWeight(new BigDecimal("16.060"), new BigDecimal("98.00")).scale())
                .isEqualTo(3);
        // 100% touch leaves the weight alone.
        assertThat(calculator.pureWeight(new BigDecimal("8.000"), new BigDecimal("100.00")))
                .isEqualByComparingTo("8.000");
        // An awkward touch still rounds half up at the third place.
        assertThat(calculator.pureWeight(new BigDecimal("10.000"), new BigDecimal("91.67")))
                .isEqualByComparingTo("9.167");
    }

    @Test
    @DisplayName("money comes out in whole rupees, rounded once at the end")
    void amountsAreWholeRupees() {
        // 1.111 x 14960 = 16,620.56 -> 16,621, not 16,620.
        assertThat(calculator.itemAmount(new BigDecimal("1.111"), PURE_RATE, BigDecimal.ZERO, BigDecimal.ZERO))
                .isEqualByComparingTo("16621");
        // Whole rupees, carried at two decimals so the paise column prints
        // blank - the same shape the rest of the system stores money in.
        BigDecimal amount = calculator.itemAmount(new BigDecimal("1.111"), PURE_RATE, BigDecimal.ZERO, BigDecimal.ZERO);
        assertThat(amount.remainder(BigDecimal.ONE)).isEqualByComparingTo("0");
        assertThat(amount.scale()).isEqualTo(2);
    }
}
