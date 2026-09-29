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
    void oldMetalAmountIsNetWeightTimesRateInWholeRupees() {
        // The shop's purchase bill: gold coin 8.000 g at 14,370
        assertThat(new OldMetalCalculator().lineAmount(new BigDecimal("8.000"), new BigDecimal("14370")))
                .isEqualByComparingTo("114960");
    }

    @Test
    void invoiceNumbersFollowTheIndianFinancialYear() {
        assertThat(DocumentNumberFormat.periodKey("FINANCIAL_YEAR", LocalDate.of(2027, 3, 31))).isEqualTo("2026");
        assertThat(DocumentNumberFormat.periodKey("FINANCIAL_YEAR", LocalDate.of(2027, 4, 1))).isEqualTo("2027");
        assertThat(DocumentNumberFormat.format("INV", "-", "2026", 6, 150)).isEqualTo("INV-2026-000150");
        assertThat(DocumentNumberFormat.format("INV", "-", "2026", 6, 150).length()).isLessThanOrEqualTo(16);
    }
}
