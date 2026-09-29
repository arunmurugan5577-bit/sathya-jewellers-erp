package com.jewellery.erp.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.ErrorCode;
import com.jewellery.erp.sales.calculation.Allocation;
import com.jewellery.erp.sales.calculation.SaleCalculator;
import com.jewellery.erp.sales.entity.PaymentStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SaleCalculatorTest {

    private final SaleCalculator calculator = new SaleCalculator();

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static SaleCalculator.LineInput line(String net, String wastage, String rate, String making, String gst) {
        return new SaleCalculator.LineInput(bd(net), bd(wastage), bd(rate), bd(making), bd(gst));
    }

    @Test
    @DisplayName("reproduces the shop's Tax Invoice No. 150 to the rupee")
    void reproducesReceipt150() {
        SaleCalculator.Result r = calculator.calculate(
                List.of(line("20.800", "30", "235", "550", "3")), bd("111"), BigDecimal.ZERO, bd("7000"));

        SaleCalculator.LineResult l = r.lines().get(0);
        assertThat(l.wastageWeightGrams()).isEqualByComparingTo("6.240");
        assertThat(l.grossWeightGrams()).isEqualByComparingTo("27.040");
        assertThat(l.amount()).isEqualByComparingTo("6904");

        assertThat(r.subtotal()).isEqualByComparingTo("6904");
        assertThat(r.cgstAmount()).isEqualByComparingTo("103.50");
        assertThat(r.sgstAmount()).isEqualByComparingTo("103.50");
        assertThat(r.taxAmount()).isEqualByComparingTo("207");
        assertThat(r.discountAmount()).isEqualByComparingTo("111");
        assertThat(r.grandTotal()).isEqualByComparingTo("7000");
        assertThat(r.netPayable()).isEqualByComparingTo("7000");
        assertThat(r.roundOffAmount()).isEqualByComparingTo("0");
        assertThat(r.balanceAmount()).isEqualByComparingTo("0");
        assertThat(r.paymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("taxes each GST rate group separately and allocates tax and discount back to lines exactly")
    void multipleRatesAndAllocation() {
        SaleCalculator.Result r = calculator.calculate(List.of(
                        line("10.000", "10", "7000", "300", "3"),
                        line("5.555", "12.5", "7000", "0", "3"),
                        line("50.000", "0", "90", "100", "5")),
                bd("99.99"), BigDecimal.ZERO, BigDecimal.ZERO);

        // 11.000 x 7000 + 300 = 77300; 6.249 x 7000 = 43743; 50 x 90 + 100 = 4600
        assertThat(r.lines()).extracting(SaleCalculator.LineResult::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(bd("77300"), bd("43743"), bd("4600"));
        // 3% of 121043 = 3631.29 -> 3631; 5% of 4600 = 230
        assertThat(r.taxAmount()).isEqualByComparingTo("3861");
        assertThat(r.cgstAmount()).isEqualByComparingTo("1930.50");

        BigDecimal cgst = r.lines().stream().map(SaleCalculator.LineResult::cgstAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = r.lines().stream().map(SaleCalculator.LineResult::discountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(cgst).isEqualByComparingTo(r.cgstAmount());
        assertThat(discount).isEqualByComparingTo("99.99");

        assertThat(r.grandTotal()).isEqualByComparingTo(r.subtotal().add(r.taxAmount()).subtract(bd("99.99")));
        // 125643 + 3861 - 99.99 = 129404.01, rounded to whole rupees
        assertThat(r.grandTotal()).isEqualByComparingTo("129404.01");
        assertThat(r.netPayable()).isEqualByComparingTo("129404");
        assertThat(r.roundOffAmount()).isEqualByComparingTo("-0.01");
        assertThat(r.paymentStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    @DisplayName("old gold comes off the grand total and payments leave a partial balance")
    void oldMetalAdjustmentAndPartialPayment() {
        SaleCalculator.Result r = calculator.calculate(
                List.of(line("20.800", "30", "235", "550", "3")), bd("111"), bd("5000"), bd("1000"));

        assertThat(r.grandTotal()).isEqualByComparingTo("7000");
        assertThat(r.netPayable()).isEqualByComparingTo("2000");
        assertThat(r.balanceAmount()).isEqualByComparingTo("1000");
        assertThat(r.paymentStatus()).isEqualTo(PaymentStatus.PARTIAL);
    }

    @Test
    void rejectsDiscountAdjustmentAndPaymentsBeyondTheTotal() {
        List<SaleCalculator.LineInput> lines = List.of(line("20.800", "30", "235", "550", "3"));

        assertThatThrownBy(() -> calculator.calculate(lines, bd("7112"), BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.DISCOUNT_EXCEEDS_TOTAL));
        assertThatThrownBy(() -> calculator.calculate(lines, bd("111"), bd("7000.01"), BigDecimal.ZERO))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OLD_METAL_AMOUNT_EXCEEDED));
        assertThatThrownBy(() -> calculator.calculate(lines, bd("111"), BigDecimal.ZERO, bd("7001")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PAYMENT_EXCEEDS_BALANCE));
    }

    @Test
    void allocationAlwaysSumsToTheWhole() {
        List<BigDecimal> parts = Allocation.distribute(bd("100.00"), List.of(bd("1"), bd("1"), bd("1")));
        assertThat(parts).extracting(BigDecimal::toPlainString).containsExactly("33.34", "33.33", "33.33");

        assertThat(Allocation.distribute(bd("0.00"), List.of(bd("5"), bd("7"))))
                .allSatisfy(p -> assertThat(p).isEqualByComparingTo("0"));
    }
}
