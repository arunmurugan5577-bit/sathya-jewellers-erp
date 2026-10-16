package com.jewellery.erp.sales.mapper;

import com.jewellery.erp.common.util.AmountInWords;
import com.jewellery.erp.inventory.entity.InventoryItem;
import com.jewellery.erp.sales.calculation.SaleCalculator;
import com.jewellery.erp.sales.dto.SaleDtos;
import com.jewellery.erp.sales.entity.Sale;
import com.jewellery.erp.sales.entity.SaleItem;
import com.jewellery.erp.sales.entity.SaleOldMetalAdjustment;
import com.jewellery.erp.sales.entity.SaleItemStatus;
import com.jewellery.erp.sales.entity.SalePayment;
import org.springframework.stereotype.Component;

@Component
public class SaleMapper {

    public SaleDtos.ItemLookup toLookup(InventoryItem item) {
        return new SaleDtos.ItemLookup(
                item.getId(),
                item.getSerialNumber(),
                item.getItemType().getName(),
                item.getPurity().getName(),
                item.getCategory().getId(),
                item.getCategory().getName(),
                item.getSubCategory() == null ? null : item.getSubCategory().getId(),
                item.getSubCategory() == null ? null : item.getSubCategory().getName(),
                item.getHsnCode().getHsnCode(),
                item.getHsnCode().getGstPercentage(),
                // A box offers what is left in it, not what arrived in it.
                item.isBulk() ? item.getRemainingWeightGrams() : item.getWeightGrams(),
                item.isBulk(),
                item.isBulk() ? item.getRemainingWeightGrams() : null,
                item.getSize(),
                item.getDescription(),
                defaultParticulars(item));
    }

    /**
     * What the invoice calls the piece: its sub category, or its category when
     * it has no sub category.
     *
     * <p>The two used to be run together - "Ladies Ring" from a Ladies sub
     * category under a Ring category - which read well for rings and badly for
     * everything whose sub category already says what the piece is. The shop
     * asked for the sub category alone.
     *
     * <p>Only the starting point: the counter can type over it line by line,
     * and whatever is typed is what the invoice keeps.
     */
    public static String defaultParticulars(InventoryItem item) {
        return item.getSubCategory() == null
                ? item.getCategory().getName()
                : item.getSubCategory().getName();
    }

    public SaleDtos.Line toPreviewLine(
            int lineNumber, InventoryItem item, String particulars, SaleCalculator.LineResult r) {
        return new SaleDtos.Line(
                lineNumber,
                item.getId(),
                item.getSerialNumber(),
                particulars,
                item.getItemType().getName(),
                item.getPurity().getName(),
                item.getCategory().getName(),
                item.getSubCategory() == null ? null : item.getSubCategory().getName(),
                item.getHsnCode().getHsnCode(),
                r.gstPercentage(),
                r.netWeightGrams(),
                r.wastagePercentage(),
                r.wastageWeightGrams(),
                r.grossWeightGrams(),
                r.ratePerGram(),
                r.makingCharge(),
                r.amount(),
                r.cgstAmount(),
                r.sgstAmount(),
                r.discountAmount(),
                SaleItemStatus.ACTIVE.name());
    }

    public SaleDtos.Totals toTotals(SaleCalculator.Result r) {
        return new SaleDtos.Totals(
                r.subtotal(),
                r.cgstAmount(),
                r.sgstAmount(),
                r.taxAmount(),
                r.discountAmount(),
                r.grandTotal(),
                r.oldMetalAdjustmentAmount(),
                r.chitAdjustmentAmount(),
                r.roundOffAmount(),
                r.netPayable(),
                r.amountPaid(),
                r.balanceAmount(),
                r.paymentStatus().name(),
                AmountInWords.rupees(r.grandTotal()),
                AmountInWords.rupees(r.netPayable()));
    }

    public SaleDtos.Detail toDetail(Sale s) {
        return new SaleDtos.Detail(
                s.getId(),
                s.getInvoiceNumber(),
                s.getInvoiceDate(),
                s.getStatus().name(),
                s.getCustomer().getId(),
                s.getCustomer().getCustomerCode(),
                s.getCustomerName(),
                s.getCustomerMobile(),
                s.getCustomerAddress(),
                s.getCustomerGstin(),
                s.getSellerName(),
                s.getSellerAddress(),
                s.getSellerMobile(),
                s.getSellerGstin(),
                s.getItems().stream().map(this::toLine).toList(),
                toTotals(s),
                s.getOldMetalAdjustments().stream().map(this::toAdjustment).toList(),
                s.getChitRedemptions().stream().map(SaleMapper::toChitCredit).toList(),
                s.getPayments().stream().map(this::toPayment).toList(),
                s.getRemarks(),
                s.getCancelReason(),
                s.getCancelledAt(),
                s.getCancelledBy(),
                s.getCreatedAt(),
                s.getCreatedBy());
    }

    private static SaleDtos.ChitCredit toChitCredit(com.jewellery.erp.chit.entity.ChitRedemption r) {
        return new SaleDtos.ChitCredit(
                r.getId(),
                r.getChitAccount().getId(),
                r.getChitAccount().getChitNumber(),
                r.getChitAccount().getSchemeType().name(),
                r.getGrams(),
                r.getRatePerGram(),
                r.getAmount(),
                r.getStatus().name());
    }

    public SaleDtos.Summary toSummary(Sale s) {
        return new SaleDtos.Summary(
                s.getId(),
                s.getInvoiceNumber(),
                s.getInvoiceDate(),
                s.getStatus().name(),
                s.getCustomer().getId(),
                s.getCustomerName(),
                s.getCustomerMobile(),
                s.getGrandTotal(),
                s.getNetPayable(),
                s.getAmountPaid(),
                s.getBalanceAmount(),
                s.getPaymentStatus().name());
    }

    private SaleDtos.Totals toTotals(Sale s) {
        return new SaleDtos.Totals(
                s.getSubtotal(),
                s.getCgstAmount(),
                s.getSgstAmount(),
                s.getTaxAmount(),
                s.getDiscountAmount(),
                s.getGrandTotal(),
                s.getOldMetalAdjustmentAmount(),
                s.getChitAdjustmentAmount(),
                s.getRoundOffAmount(),
                s.getNetPayable(),
                s.getAmountPaid(),
                s.getBalanceAmount(),
                s.getPaymentStatus().name(),
                AmountInWords.rupees(s.getGrandTotal()),
                AmountInWords.rupees(s.getNetPayable()));
    }

    private SaleDtos.Line toLine(SaleItem i) {
        return new SaleDtos.Line(
                i.getLineNumber(),
                i.getInventoryItem().getId(),
                i.getSerialNumber(),
                i.getParticulars(),
                i.getItemType().getName(),
                i.getPurity().getName(),
                i.getCategory().getName(),
                i.getSubCategory() == null ? null : i.getSubCategory().getName(),
                i.getHsnCode(),
                i.getGstPercentage(),
                i.getNetWeightGrams(),
                i.getWastagePercentage(),
                i.getWastageWeightGrams(),
                i.getGrossWeightGrams(),
                i.getRatePerGram(),
                i.getMakingCharge(),
                i.getAmount(),
                i.getCgstAmount(),
                i.getSgstAmount(),
                i.getDiscountAmount(),
                i.getLineStatus().name());
    }

    private SaleDtos.Adjustment toAdjustment(SaleOldMetalAdjustment a) {
        return new SaleDtos.Adjustment(
                a.getId(),
                a.getOldMetalTransaction().getId(),
                a.getOldMetalTransaction().getTransactionNumber(),
                a.getOldMetalTransaction().getTransactionDate(),
                a.getAdjustmentAmount(),
                a.getStatus().name());
    }

    private SaleDtos.Payment toPayment(SalePayment p) {
        return new SaleDtos.Payment(
                p.getId(),
                p.getPaymentMethod().name(),
                p.getAmount(),
                p.getReferenceNumber(),
                p.getPaymentDate(),
                p.getRemarks(),
                p.getCreatedAt(),
                p.getCreatedBy());
    }
}
