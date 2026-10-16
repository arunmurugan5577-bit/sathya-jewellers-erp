package com.jewellery.erp.chit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jewellery.erp.chit.service.ChitCalculator;
import com.jewellery.erp.common.exception.BusinessRuleException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The shop's chit sheet, reproduced figure for figure.
 *
 * <p>Every gram below is taken from the sheet the owner keeps, at the rate of
 * 13,675 written on it. Three of them - 0.365, 2.632 and 4.387 - are the ones
 * that pin the rounding: normal rounding would give 0.366, 2.633 and 4.388, so
 * these tests fail the moment somebody tidies the calculator into line with
 * the half-up rule the rest of the application uses.
 */
class ChitCalculatorTest {

    private final ChitCalculator calculator = new ChitCalculator();
    private static final BigDecimal RATE = new BigDecimal("13675");

    @Nested
    @DisplayName("Gold bought by an instalment")
    class GramsBought {

        @Test
        @DisplayName("the three gold schemes, at the rate on the sheet")
        void matchesTheSheet() {
            assertThat(calculator.gramsFor(new BigDecimal("3000"), RATE)).isEqualByComparingTo("0.219");
            assertThat(calculator.gramsFor(new BigDecimal("5000"), RATE)).isEqualByComparingTo("0.365");
            assertThat(calculator.gramsFor(new BigDecimal("10000"), RATE)).isEqualByComparingTo("0.731");
        }

        @Test
        @DisplayName("and a whole chit's worth at one rate")
        void matchesTheSheetAtMaturity() {
            assertThat(calculator.gramsFor(new BigDecimal("36000"), RATE)).isEqualByComparingTo("2.632");
            assertThat(calculator.gramsFor(new BigDecimal("60000"), RATE)).isEqualByComparingTo("4.387");
            assertThat(calculator.gramsFor(new BigDecimal("120000"), RATE)).isEqualByComparingTo("8.775");
        }

        @Test
        @DisplayName("grams are rounded down, never up")
        void roundsDown() {
            // 5,000 / 13,675 is 0.365631. Rounded the usual way that is 0.366,
            // and the shop writes 0.365. The customer's passbook has to agree
            // with the owner's book, and a floor never credits gold that was
            // not bought.
            assertThat(calculator.gramsFor(new BigDecimal("5000"), RATE))
                    .as("0.365631 floors to 0.365, it does not round to 0.366")
                    .isEqualByComparingTo("0.365");
            assertThat(calculator.gramsFor(new BigDecimal("13674.999"), RATE))
                    .as("a hair under a gram is not a gram")
                    .isEqualByComparingTo("0.999");
        }

        @Test
        @DisplayName("a rate of nothing is refused rather than dividing by zero")
        void refusesNoRate() {
            assertThatThrownBy(() -> calculator.gramsFor(new BigDecimal("3000"), BigDecimal.ZERO))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("rate");
            assertThatThrownBy(() -> calculator.gramsFor(new BigDecimal("3000"), null))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    @DisplayName("What the chit comes to")
    class Maturity {

        @Test
        @DisplayName("eleven months of money, twelve months of value")
        void twelveMonthsOfValue() {
            assertThat(calculator.maturityAmount(new BigDecimal("500"), (short) 12))
                    .isEqualByComparingTo("6000");
            assertThat(calculator.maturityAmount(new BigDecimal("1000"), (short) 12))
                    .isEqualByComparingTo("12000");
            assertThat(calculator.maturityAmount(new BigDecimal("2000"), (short) 12))
                    .isEqualByComparingTo("24000");
            assertThat(calculator.maturityAmount(new BigDecimal("3000"), (short) 12))
                    .isEqualByComparingTo("36000");
            assertThat(calculator.maturityAmount(new BigDecimal("5000"), (short) 12))
                    .isEqualByComparingTo("60000");
            assertThat(calculator.maturityAmount(new BigDecimal("10000"), (short) 12))
                    .isEqualByComparingTo("120000");
        }

        @Test
        @DisplayName("what is still owed counts the eleven, not the twelve")
        void remainingCountsOnlyWhatTheCustomerPays() {
            // Nine paid of eleven: two left, not three. The bonus is the
            // shop's and is never asked for.
            assertThat(calculator.remainingToPay(new BigDecimal("2000"), (short) 11, (short) 9))
                    .isEqualByComparingTo("4000");
            assertThat(calculator.remainingToPay(new BigDecimal("2000"), (short) 11, (short) 11))
                    .isEqualByComparingTo("0");
            assertThat(calculator.remainingToPay(new BigDecimal("2000"), (short) 11, (short) 12))
                    .as("the bonus does not take it negative")
                    .isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("Taking it as money")
    class CashValue {

        @Test
        @DisplayName("a money chit is worth what went into it")
        void moneyChitIsItsBalance() {
            assertThat(calculator.cashValue(new BigDecimal("6000.00"), BigDecimal.ZERO, null))
                    .isEqualByComparingTo("6000.00");
        }

        @Test
        @DisplayName("a gold chit is worth what its grams fetch on the day")
        void goldChitIsValuedAtTheDaysRate() {
            // The whole point of saving in gold: 2.632 g bought at 13,675 is
            // worth more when the rate has moved to 14,500.
            assertThat(calculator.cashValue(BigDecimal.ZERO, new BigDecimal("2.632"), new BigDecimal("14500")))
                    .isEqualByComparingTo("38164.00");
            assertThat(calculator.cashValue(BigDecimal.ZERO, new BigDecimal("2.632"), RATE))
                    .as("and the rate it was bought at gives back what was paid, near enough")
                    .isEqualByComparingTo("35993.00");
        }

        @Test
        @DisplayName("valuing grams without a rate is refused")
        void refusesGramsWithoutARate() {
            assertThatThrownBy(() -> calculator.cashValue(BigDecimal.ZERO, new BigDecimal("2.632"), null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("rate");
        }
    }
}
