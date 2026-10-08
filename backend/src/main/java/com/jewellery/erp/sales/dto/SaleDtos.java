package com.jewellery.erp.sales.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Response shapes for sales. */
public final class SaleDtos {

    private SaleDtos() {}

    /** What the counter sees the moment a serial number is typed. */
    @Schema(name = "SaleItemLookup")
    public record ItemLookup(
            Long inventoryItemId,
            @Schema(example = "000123") String serialNumber,
            String itemTypeName,
            String purityName,
            Long categoryId,
            String categoryName,
            Long subCategoryId,
            String subCategoryName,
            @Schema(example = "7113") String hsnCode,
            @Schema(example = "3.00") BigDecimal gstPercentage,
            @Schema(description = "Net weight of the piece. For a bulk box, what is still in it.",
                    example = "20.800") BigDecimal netWeightGrams,
            @Schema(description = "A box sold by weight: the counter enters how much is going out.",
                    example = "false") boolean bulk,
            @Schema(description = "Grams still in the bulk box. Null for a single piece.",
                    example = "92.500") BigDecimal remainingWeightGrams,
            String size,
            String description,
            @Schema(example = "Ladies Ring") String suggestedParticulars) {}

    @Schema(name = "SaleLine")
    public record Line(
            int lineNumber,
            Long inventoryItemId,
            String serialNumber,
            String particulars,
            String itemTypeName,
            String purityName,
            String categoryName,
            String subCategoryName,
            String hsnCode,
            BigDecimal gstPercentage,
            BigDecimal netWeightGrams,
            BigDecimal wastagePercentage,
            BigDecimal wastageWeightGrams,
            BigDecimal grossWeightGrams,
            BigDecimal ratePerGram,
            BigDecimal makingCharge,
            BigDecimal amount,
            BigDecimal cgstAmount,
            BigDecimal sgstAmount,
            BigDecimal discountAmount,
            String lineStatus) {}

    @Schema(name = "SaleTotals")
    public record Totals(
            @Schema(example = "6904.00") BigDecimal subtotal,
            @Schema(example = "103.50") BigDecimal cgstAmount,
            @Schema(example = "103.50") BigDecimal sgstAmount,
            @Schema(example = "207.00") BigDecimal taxAmount,
            @Schema(example = "111.00") BigDecimal discountAmount,
            @Schema(example = "7000.00") BigDecimal grandTotal,
            BigDecimal oldMetalAdjustmentAmount,
            BigDecimal roundOffAmount,
            BigDecimal netPayable,
            BigDecimal amountPaid,
            BigDecimal balanceAmount,
            String paymentStatus,
            @Schema(example = "Seven Thousand Rupees Only") String grandTotalInWords,
            String netPayableInWords) {}

    /** The server's preview of an invoice that has not been saved. */
    @Schema(name = "SaleCalculation")
    public record Calculation(List<Line> lines, Totals totals) {}

    @Schema(name = "SaleOldMetalAdjustment")
    public record Adjustment(
            Long id,
            Long oldMetalTransactionId,
            String transactionNumber,
            LocalDate transactionDate,
            BigDecimal adjustmentAmount,
            String status) {}

    @Schema(name = "SalePayment")
    public record Payment(
            Long id,
            String method,
            BigDecimal amount,
            String referenceNumber,
            LocalDate paymentDate,
            String remarks,
            Instant createdAt,
            String createdBy) {}

    /** Everything the printed tax invoice shows, plus payment and cancellation state. */
    @Schema(name = "Sale")
    public record Detail(
            Long id,
            @Schema(example = "INV-2026-000150") String invoiceNumber,
            LocalDate invoiceDate,
            String status,
            Long customerId,
            String customerCode,
            String customerName,
            String customerMobile,
            String customerAddress,
            String customerGstin,
            String sellerName,
            String sellerAddress,
            String sellerMobile,
            String sellerGstin,
            List<Line> items,
            Totals totals,
            List<Adjustment> oldMetalAdjustments,
            List<Payment> payments,
            String remarks,
            String cancelReason,
            Instant cancelledAt,
            String cancelledBy,
            Instant createdAt,
            String createdBy) {}

    @Schema(name = "SaleSummary")
    public record Summary(
            Long id,
            String invoiceNumber,
            LocalDate invoiceDate,
            String status,
            Long customerId,
            String customerName,
            String customerMobile,
            BigDecimal grandTotal,
            BigDecimal netPayable,
            BigDecimal amountPaid,
            BigDecimal balanceAmount,
            String paymentStatus) {}
}
