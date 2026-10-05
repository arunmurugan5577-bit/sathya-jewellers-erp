package com.jewellery.erp.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.jewellery.erp.common.util.AmountInWords;
import com.jewellery.erp.numbering.DocumentNumberFormat;
import com.jewellery.erp.oldmetal.service.OldMetalCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BillingHelpersTest {

    @Test
    void amountInWordsUsesTheIndianNumberingSystem() {
        assertThat(AmountInWords.rupees(new BigDecimal("7000"))).isEqualTo("Seven Thousand Rupees Only");
        assertThat(AmountInWords.rupees(new BigDecimal("114960")))
                .isEqualTo("One Lakh Fourteen Thousand Nine Hundred Sixty Rupees Only");
    }

    @Test
    void oldMetalIsPaidOnTheWeightOnTheScale() {
        OldMetalCalculator calculator = new OldMetalCalculator();
        // Bought as weighed: the customer is paid for the stones too.
        assertThat(calculator.lineAmount(new BigDecimal("120.000"), new BigDecimal("100.000"), new BigDecimal("100")))
                .isEqualByComparingTo("12000");
    }

    @Test
    void aPieceWithNothingToDeductIsPaidOnItsNetWeight() {
        OldMetalCalculator calculator = new OldMetalCalculator();
        // The shop's purchase bill No. 74: gold coin 8.000 g at 14,370, no
        // gross weight because a coin has no stones to take off.
        assertThat(calculator.lineAmount(null, new BigDecimal("8.000"), new BigDecimal("14370")))
                .isEqualByComparingTo("114960");
        assertThat(calculator.payableWeight(null, new BigDecimal("8.000"))).isEqualByComparingTo("8.000");
    }

    @Test
    void thePayableWeightIsTheGrossWhenThereIsOne() {
        OldMetalCalculator calculator = new OldMetalCalculator();
        assertThat(calculator.payableWeight(new BigDecimal("120.000"), new BigDecimal("100.000")))
                .isEqualByComparingTo("120.000");
    }

    @Test
    void invoiceNumbersFollowTheIndianFinancialYear() {
        assertThat(DocumentNumberFormat.periodKey("FINANCIAL_YEAR", LocalDate.of(2027, 3, 31))).isEqualTo("2026");
        assertThat(DocumentNumberFormat.periodKey("FINANCIAL_YEAR", LocalDate.of(2027, 4, 1))).isEqualTo("2027");
        assertThat(DocumentNumberFormat.format("INV", "-", "2026", 6, 150)).isEqualTo("INV-2026-000150");
        assertThat(DocumentNumberFormat.format("INV", "-", "2026", 6, 150).length()).isLessThanOrEqualTo(16);
    }
}
