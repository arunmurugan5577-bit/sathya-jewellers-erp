package com.jewellery.erp.oldmetal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Response shapes for old gold / silver purchases, grouped because they only make sense together. */
public final class OldMetalDtos {

    private OldMetalDtos() {}

    @Schema(name = "OldMetalItem")
    public record Item(
            int lineNumber,
            Long itemTypeId,
            String itemTypeName,
            Long purityId,
            String purityName,
            String particulars,
            String hsnCode,
            BigDecimal netWeightGrams,
            BigDecimal grossWeightGrams,
            BigDecimal ratePerGram,
            BigDecimal amount) {}

    /** Everything the purchase bill prints, plus usage state. */
    @Schema(name = "OldMetalTransaction")
    public record Detail(
            Long id,
            @Schema(example = "PB-2026-000074") String transactionNumber,
            LocalDate transactionDate,
            Long customerId,
            String customerName,
            String customerMobile,
            String customerAddress,
            String sellerName,
            String sellerAddress,
            String sellerMobile,
            String sellerGstin,
            BigDecimal totalAmount,
            BigDecimal usedAmount,
            BigDecimal availableAmount,
            String status,
            @Schema(example = "One Lakh Fourteen Thousand Nine Hundred Sixty Rupees Only") String amountInWords,
            String remarks,
            String cancelReason,
            Instant cancelledAt,
            String cancelledBy,
            List<Item> items,
            Instant createdAt,
            String createdBy) {}

    @Schema(name = "OldMetalSummary")
    public record Summary(
            Long id,
            String transactionNumber,
            LocalDate transactionDate,
            Long customerId,
            String customerName,
            String customerMobile,
            BigDecimal totalAmount,
            BigDecimal usedAmount,
            BigDecimal availableAmount,
            String status) {}

    /** A bill offered in the sale form's old gold / silver picker. */
    @Schema(name = "OldMetalOption")
    public record Option(
            Long id,
            String transactionNumber,
            LocalDate transactionDate,
            @Schema(example = "Gold coin 8.000 g @ 14370.00") String description,
            BigDecimal totalAmount,
            BigDecimal usedAmount,
            BigDecimal availableAmount,
            String status) {}
}
