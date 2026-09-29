package com.jewellery.erp.sales.calculation;

import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.ErrorCode;
import com.jewellery.erp.common.util.Money;
import com.jewellery.erp.sales.entity.PaymentStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The sales invoice arithmetic. The single source of truth for every figure on a
 * tax invoice; the Angular form shows a preview by calling the server, never by
 * repeating these formulas.
 *
 * <p>The specification is the shop's own Tax Invoice No. 150, reproduced exactly by
 * {@code SaleCalculatorTest}:
 *
 * <pre>
 *   Net 20.800 g, wastage 30%   ->  wastage 6.240 g  ->  gross 27.040 g
 *   27.040 g x rate 235 = 6,354.40  +  making 550 = 6,904.40  ->  Amount 6,904
 *   TOTAL                                                             6,904
 *   Add CGST 1.5% + SGST 1.5%     (3% of 6,904 = 207.12)  ->           207
 *   Discount                                                          - 111
 *   GRAND TOTAL                                                       7,000
 * </pre>
 *
 * <p>Rules that follow from it:
 *
 * <ul>
 *   <li>Wastage is a percentage of net weight; gross weight = net + wastage weight.
 *   <li>Making charge is a flat amount per piece, added after weight x rate.
 *   <li>Line amounts and GST are billed in whole rupees (the receipt's paise column is blank).
 *   <li>GST is charged on the TOTAL, before the discount. The rate comes from each
 *       piece's HSN code; lines with different rates are taxed as separate groups.
 *   <li>The discount comes off after GST.
 *   <li>Old gold / silver adjustments come off the GRAND TOTAL, giving the amount payable.
 * </ul>
 *
 * <p>A formula change is a change to this class alone.
 */
@Component
public class SaleCalculator {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    /** What the calculator needs to know about one piece. */
    public record LineInput(
            BigDecimal netWeightGrams,
            BigDecimal wastagePercentage,
            BigDecimal ratePerGram,
            BigDecimal makingCharge,
            BigDecimal gstPercentage) {}

    public record LineResult(
            BigDecimal netWeightGrams,
            BigDecimal wastagePercentage,
            BigDecimal wastageWeightGrams,
            BigDecimal grossWeightGrams,
            BigDecimal ratePerGram,
            BigDecimal makingCharge,
            BigDecimal gstPercentage,
            BigDecimal amount,
            BigDecimal cgstAmount,
            BigDecimal sgstAmount,
            BigDecimal discountAmount) {}

    public record Result(
            List<LineResult> lines,
            BigDecimal subtotal,
            BigDecimal cgstAmount,
            BigDecimal sgstAmount,
            BigDecimal taxAmount,
            BigDecimal discountAmount,
            BigDecimal grandTotal,
            BigDecimal oldMetalAdjustmentAmount,
            BigDecimal roundOffAmount,
            BigDecimal netPayable,
            BigDecimal amountPaid,
            BigDecimal balanceAmount,
            PaymentStatus paymentStatus) {}

    public Result calculate(
            List<LineInput> inputs, BigDecimal discount, BigDecimal oldMetalAdjustment, BigDecimal amountPaid) {

        // --- 1. Each piece: weight, wastage, amount ----------------------------
        List<Priced> priced = new ArrayList<>(inputs.size());
        BigDecimal subtotal = Money.ZERO;
        for (LineInput input : inputs) {
            BigDecimal net = Money.weight(input.netWeightGrams());
            BigDecimal wastagePct = Money.money(Money.nullToZero(input.wastagePercentage()));
            BigDecimal wastageWeight = Money.weight(Money.percentOf(net, wastagePct));
            BigDecimal gross = net.add(wastageWeight);
            BigDecimal rate = Money.money(input.ratePerGram());
            BigDecimal making = Money.money(Money.nullToZero(input.makingCharge()));
            BigDecimal amount = Money.rupees(gross.multiply(rate).add(making));

            priced.add(new Priced(net, wastagePct, wastageWeight, gross, rate, making,
                    Money.money(input.gstPercentage()), amount));
            subtotal = subtotal.add(amount);
        }

        // --- 2. GST on the total, per rate group, split equally ---------------
        BigDecimal[] cgstByLine = new BigDecimal[priced.size()];
        BigDecimal cgstTotal = Money.ZERO;
        for (Map.Entry<BigDecimal, List<Integer>> group : groupByRate(priced).entrySet()) {
            List<Integer> indexes = group.getValue();
            BigDecimal groupBase = indexes.stream()
                    .map(i -> priced.get(i).amount())
                    .reduce(Money.ZERO, BigDecimal::add);
            BigDecimal groupTax = Money.rupees(Money.percentOf(groupBase, group.getKey()));
            // Whole rupees halve exactly to the paisa: 207 -> 103.50 + 103.50.
            BigDecimal half = groupTax.divide(TWO, Money.MONEY_SCALE, Money.ROUNDING);

            List<BigDecimal> shares = Allocation.distribute(
                    half, indexes.stream().map(i -> priced.get(i).amount()).toList());
            for (int k = 0; k < indexes.size(); k++) {
                cgstByLine[indexes.get(k)] = shares.get(k);
            }
            cgstTotal = cgstTotal.add(half);
        }
        // CGST and SGST are equal halves, so each line's SGST equals its CGST.
        BigDecimal sgstTotal = cgstTotal;
        BigDecimal taxTotal = cgstTotal.add(sgstTotal);

        // --- 3. Discount, after tax -------------------------------------------
        BigDecimal discountAmount = Money.money(Money.nullToZero(discount));
        if (discountAmount.signum() < 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "discountAmount",
                    "Discount cannot be negative.");
        }
        BigDecimal beforeDiscount = subtotal.add(taxTotal);
        if (discountAmount.compareTo(beforeDiscount) > 0) {
            throw new BusinessRuleException(ErrorCode.DISCOUNT_EXCEEDS_TOTAL, "discountAmount",
                    "Discount of %s is more than the invoice total of %s.".formatted(discountAmount, beforeDiscount));
        }
        BigDecimal grandTotal = beforeDiscount.subtract(discountAmount);

        List<BigDecimal> lineTotals = new ArrayList<>();
        for (int i = 0; i < priced.size(); i++) {
            lineTotals.add(priced.get(i).amount().add(cgstByLine[i]).add(cgstByLine[i]));
        }
        List<BigDecimal> discountByLine = Allocation.distribute(discountAmount, lineTotals);

        // --- 4. Old gold / silver, round-off, payment -------------------------
        BigDecimal adjustment = Money.money(Money.nullToZero(oldMetalAdjustment));
        if (adjustment.compareTo(grandTotal) > 0) {
            throw new BusinessRuleException(ErrorCode.OLD_METAL_AMOUNT_EXCEEDED, "oldMetalAdjustments",
                    "Old gold/silver adjustment of %s is more than the grand total of %s."
                            .formatted(adjustment, grandTotal));
        }
        BigDecimal beforeRounding = grandTotal.subtract(adjustment);
        BigDecimal netPayable = Money.rupees(beforeRounding);
        BigDecimal roundOff = netPayable.subtract(beforeRounding);

        BigDecimal paid = Money.money(Money.nullToZero(amountPaid));
        if (paid.compareTo(netPayable) > 0) {
            throw new BusinessRuleException(ErrorCode.PAYMENT_EXCEEDS_BALANCE, "payments",
                    "Payments of %s are more than the amount payable of %s.".formatted(paid, netPayable));
        }
        BigDecimal balance = netPayable.subtract(paid);

        List<LineResult> lines = new ArrayList<>(priced.size());
        for (int i = 0; i < priced.size(); i++) {
            Priced p = priced.get(i);
            lines.add(new LineResult(p.net(), p.wastagePct(), p.wastageWeight(), p.gross(), p.rate(), p.making(),
                    p.gstPct(), p.amount(), cgstByLine[i], cgstByLine[i], discountByLine.get(i)));
        }

        return new Result(lines, subtotal, cgstTotal, sgstTotal, taxTotal, discountAmount, grandTotal,
                adjustment, roundOff, netPayable, paid, balance, paymentStatus(netPayable, paid));
    }

    /** What is owed after a further payment is recorded against an existing invoice. */
    public PaymentStatus paymentStatus(BigDecimal netPayable, BigDecimal paid) {
        if (paid.compareTo(netPayable) >= 0) {
            return PaymentStatus.PAID;
        }
        return paid.signum() == 0 ? PaymentStatus.UNPAID : PaymentStatus.PARTIAL;
    }

    private static Map<BigDecimal, List<Integer>> groupByRate(List<Priced> priced) {
        // LinkedHashMap keeps groups in first-seen line order, so the allocation is
        // deterministic. Rates are all scale 2, so equal rates are equal keys.
        Map<BigDecimal, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < priced.size(); i++) {
            groups.computeIfAbsent(priced.get(i).gstPct(), key -> new ArrayList<>()).add(i);
        }
        return groups;
    }

    private record Priced(
            BigDecimal net,
            BigDecimal wastagePct,
            BigDecimal wastageWeight,
            BigDecimal gross,
            BigDecimal rate,
            BigDecimal making,
            BigDecimal gstPct,
            BigDecimal amount) {}
}
