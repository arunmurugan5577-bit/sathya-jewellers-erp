package com.jewellery.erp.wholesale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Request and response shapes for wholesale estimates. */
public final class WholesaleDtos {

    private WholesaleDtos() {}

    // ------------------------------------------------------------ requests ---

    /** One piece on an estimate. The piece itself is identified by serial. */
    @Schema(name = "WholesaleEstimateItemRequest")
    public record ItemRequest(
            @Schema(example = "000123", description = "A piece in stock; everything printed is read from it")
            @NotBlank(message = "Serial number is required")
            @Size(max = 6, message = "A serial number is at most 6 digits")
            String serialNumber,

            @Schema(example = "G.CHAIN 916 ROUND", description = "Defaults to the piece's own description")
            @Size(max = 200, message = "Jewel name must not exceed 200 characters")
            String jewelName,

            @Schema(example = "98.00", description = "Touch, out of 100")
            @NotNull(message = "Pure percent is required")
            @DecimalMin(value = "0.01", message = "Pure percent must be greater than zero")
            @DecimalMax(value = "100", message = "Pure percent cannot exceed 100")
            @Digits(integer = 3, fraction = 2) BigDecimal purePercentage,

            @Schema(example = "14960", description = "Defaults to the estimate's pure rate")
            @DecimalMin(value = "0.01", message = "Rate must be greater than zero")
            @Digits(integer = 10, fraction = 2) BigDecimal ratePerGram,

            @Schema(example = "160")
            @DecimalMin(value = "0", message = "Making charge cannot be negative")
            @Digits(integer = 10, fraction = 2) BigDecimal makingCharge,

            @Schema(example = "0")
            @DecimalMin(value = "0", message = "Stone amount cannot be negative")
            @Digits(integer = 10, fraction = 2) BigDecimal stoneAmount) {}

    /**
     * A new estimate, or a preview of one. {@code customerId} is optional for a
     * preview so the form can price pieces before the party is chosen, and
     * required to save.
     */
    @Schema(name = "WholesaleEstimateRequest")
    public record Request(
            Long customerId,

            @Schema(description = "Defaults to today. Cannot be in the future.") LocalDate estimateDate,

            @Schema(example = "14960", description = "One gram of pure gold on the day")
            @NotNull(message = "Pure rate is required")
            @DecimalMin(value = "0.01", message = "Pure rate must be greater than zero")
            @Digits(integer = 10, fraction = 2) BigDecimal pureRatePerGram,

            @NotEmpty(message = "Add at least one piece")
            @Size(max = 50, message = "An estimate can have at most 50 pieces")
            List<@Valid @NotNull ItemRequest> items,

            @Size(max = 500, message = "Remarks must not exceed 500 characters") String remarks) {

        public List<ItemRequest> itemsOrEmpty() {
            return items == null ? List.of() : items;
        }
    }

    @Schema(name = "WholesaleCancelRequest")
    public record CancelRequest(
            @Size(max = 500, message = "Reason must not exceed 500 characters") String reason) {}

    // ----------------------------------------------------------- responses ---

    /** A piece in stock, priced for the estimate form. */
    @Schema(name = "WholesaleItemLookup")
    public record ItemLookup(
            Long inventoryItemId,
            String serialNumber,
            String jewelName,
            String itemTypeName,
            String purityName,
            @Schema(example = "16.060") BigDecimal jewelWeightGrams,
            @Schema(example = "98.00", description = "Suggested touch, derived from the purity master")
            BigDecimal suggestedPurePercentage) {}

    @Schema(name = "WholesaleEstimateLine")
    public record Line(
            int lineNumber,
            Long inventoryItemId,
            String serialNumber,
            String jewelName,
            BigDecimal jewelWeightGrams,
            BigDecimal purePercentage,
            BigDecimal pureWeightGrams,
            BigDecimal ratePerGram,
            BigDecimal makingCharge,
            BigDecimal stoneAmount,
            BigDecimal itemAmount,
            String lineStatus) {}

    /** Everything the printed estimate shows below the lines. */
    @Schema(name = "WholesaleTotals")
    public record Totals(
            @Schema(example = "3.373") BigDecimal openingPureGrams,
            @Schema(example = "0.00") BigDecimal openingMiscAmount,
            @Schema(example = "50460.00") BigDecimal openingValue,
            @Schema(example = "15.739") BigDecimal totalPureGrams,
            @Schema(example = "160.00") BigDecimal totalMiscAmount,
            @Schema(example = "235615.00") BigDecimal totalAmount,
            @Schema(example = "19.112") BigDecimal closingPureGrams,
            @Schema(example = "160.00") BigDecimal closingMiscAmount,
            @Schema(example = "286076.00") BigDecimal closingValue,
            String totalAmountInWords) {}

    /** The server's pricing of an estimate that has not been saved. */
    @Schema(name = "WholesaleCalculation")
    public record Calculation(List<Line> lines, Totals totals) {}

    @Schema(name = "WholesaleEstimate")
    public record Detail(
            Long id,
            @Schema(example = "WE-2026-000003") String estimateNumber,
            LocalDate estimateDate,
            String status,
            Long customerId,
            String customerCode,
            String customerName,
            String customerMobile,
            String sellerName,
            String sellerAddress,
            String sellerMobile,
            BigDecimal pureRatePerGram,
            List<Line> items,
            Totals totals,
            String remarks,
            String cancelReason,
            Instant cancelledAt,
            String cancelledBy,
            Instant createdAt,
            String createdBy) {}

    @Schema(name = "WholesaleEstimateSummary")
    public record Summary(
            Long id,
            String estimateNumber,
            LocalDate estimateDate,
            String customerName,
            BigDecimal totalPureGrams,
            BigDecimal totalAmount,
            BigDecimal closingPureGrams,
            BigDecimal closingValue,
            String status) {}

    /** A party's account as it stands now. */
    @Schema(name = "WholesaleBalance")
    public record Balance(
            Long customerId,
            String customerName,
            @Schema(example = "19.112") BigDecimal pureGrams,
            @Schema(example = "160.00") BigDecimal miscAmount,
            @Schema(description = "Valued at the rate given, or null when none was")
            BigDecimal valueAtRate) {}
}
