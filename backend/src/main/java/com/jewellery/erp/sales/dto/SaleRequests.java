package com.jewellery.erp.sales.dto;

import com.jewellery.erp.sales.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What the counter sends. Deliberately contains no computed figure - no amount,
 * tax, weight or total. The server looks up the piece by serial number and works
 * everything out; a tampered client can change only what a salesperson could.
 */
public final class SaleRequests {

    private SaleRequests() {}

    /**
     * One line of the invoice: a tagged piece, or a loose one.
     *
     * <p>A tagged piece needs only its serial number - the server reads the
     * rest off the piece. A loose piece has no serial and no stock record, so
     * what would have been read off the piece is typed instead: the small gold
     * a shop sells without ever tagging it.
     *
     * <p>Which kind a line is follows from whether a serial was sent. Nothing
     * else distinguishes them and nothing else should: a line with a serial
     * and a hand-typed purity would leave two answers to the same question.
     */
    @Schema(name = "SaleItemRequest")
    public record Item(
            @Schema(example = "000123", description = "A tagged piece. Leave out for a loose one.")
            @Pattern(regexp = "^\\s*\\d{1,6}\\s*$", message = "Serial number must be up to six digits")
            String serialNumber,

            @Schema(example = "AZ1234", description = "The BIS hallmark stamped on the piece, if it carries one")
            @Pattern(regexp = "^\\s*[0-9A-Za-z]{6}\\s*$", message = "A HUID is six letters or digits")
            String huid,

            @Schema(description = "Loose piece only: what it is made of") Long itemTypeId,
            @Schema(description = "Loose piece only: its purity") Long purityId,
            @Schema(description = "Loose piece only: its category") Long categoryId,
            @Schema(description = "Loose piece only: its sub category, if it has one") Long subCategoryId,
            @Schema(description = "Loose piece only: the HSN code it is taxed under") Long hsnId,

            @Schema(description = "Printed description. Defaults to the piece's category and sub category.",
                    example = "Ladies Ring")
            @Size(max = 200, message = "Particulars must not exceed 200 characters")
            String particulars,

            @Schema(example = "4.100",
                    description = "Weight being sold out of a bulk box, or the whole weight of a loose "
                            + "piece. A tagged single piece is sold whole and its own weight is used.")
            @DecimalMin(value = "0.001", message = "The weight sold must be greater than zero")
            @Digits(integer = 9, fraction = 3, message = "Weight allows at most 3 decimal places")
            BigDecimal weightGrams,

            @Schema(example = "30")
            @DecimalMin(value = "0", message = "Wastage cannot be negative")
            @DecimalMax(value = "100", message = "Wastage cannot exceed 100%")
            @Digits(integer = 3, fraction = 2, message = "Wastage allows up to 2 decimal places")
            BigDecimal wastagePercentage,

            @Schema(example = "235")
            @NotNull(message = "Rate per gram is required")
            @DecimalMin(value = "0.01", message = "Rate must be greater than zero")
            @Digits(integer = 10, fraction = 2, message = "Rate allows up to 2 decimal places")
            BigDecimal ratePerGram,

            @Schema(example = "550")
            @DecimalMin(value = "0", message = "Making charge cannot be negative")
            @Digits(integer = 12, fraction = 2, message = "Making charge allows up to 2 decimal places")
            BigDecimal makingCharge) {}

    @Schema(name = "SaleOldMetalAdjustmentRequest")
    public record OldMetalAdjustment(
            @NotNull(message = "Purchase bill is required") Long transactionId,

            @NotNull(message = "Adjustment amount is required")
            @DecimalMin(value = "0.01", message = "Adjustment must be greater than zero")
            @Digits(integer = 12, fraction = 2, message = "Amount allows up to 2 decimal places")
            BigDecimal amount) {}

    /**
     * A matured chit put against this invoice.
     *
     * <p>Asked for differently by kind, because the two hold different things.
     * A money chit is spent in rupees - {@code amount}. A gold chit is spent
     * in grams at the rate on the day - {@code grams} and {@code ratePerGram}
     * - and its value follows from those; asking for an amount would mean
     * dividing back into grams and losing a milligram doing it.
     */
    @Schema(name = "SaleChitRedemptionRequest")
    public record ChitRedemption(
            @NotNull(message = "Chit is required") Long chitAccountId,

            @Schema(example = "24000.00", description = "Money schemes only")
            @DecimalMin(value = "0.01", message = "The amount must be greater than zero")
            @Digits(integer = 12, fraction = 2, message = "Amount allows up to 2 decimal places")
            BigDecimal amount,

            @Schema(example = "2.632", description = "Gold schemes only: the grams to take off the scheme")
            @DecimalMin(value = "0.001", message = "The weight must be greater than zero")
            @Digits(integer = 9, fraction = 3, message = "Weight allows at most 3 decimal places")
            BigDecimal grams,

            @Schema(example = "14800.00", description = "Gold schemes only: today's rate")
            @DecimalMin(value = "0.01", message = "The rate must be greater than zero")
            @Digits(integer = 10, fraction = 2, message = "Rate allows up to 2 decimal places")
            BigDecimal ratePerGram) {}

    @Schema(name = "SalePaymentRequest")
    public record Payment(
            @NotNull(message = "Payment method is required") PaymentMethod method,

            @NotNull(message = "Payment amount is required")
            @DecimalMin(value = "0.01", message = "Payment must be greater than zero")
            @Digits(integer = 12, fraction = 2, message = "Amount allows up to 2 decimal places")
            BigDecimal amount,

            @Size(max = 100, message = "Reference must not exceed 100 characters") String referenceNumber,

            @Size(max = 300, message = "Remarks must not exceed 300 characters") String remarks) {}

    /**
     * A new invoice, or a preview of one. {@code customerId} is optional for a
     * preview (the form can price pieces before the customer is chosen) and
     * required to complete the sale.
     */
    @Schema(name = "SaleRequest")
    public record Sale(
            Long customerId,

            @Schema(description = "Defaults to today. Cannot be in the future.") LocalDate invoiceDate,

            @NotEmpty(message = "Add at least one item")
            @Size(max = 50, message = "An invoice can have at most 50 items")
            List<@Valid @NotNull Item> items,

            @DecimalMin(value = "0", message = "Discount cannot be negative")
            @Digits(integer = 12, fraction = 2, message = "Discount allows up to 2 decimal places")
            BigDecimal discountAmount,

            @Size(max = 10, message = "At most 10 old gold / silver bills per invoice")
            List<@Valid @NotNull OldMetalAdjustment> oldMetalAdjustments,

            @Size(max = 5, message = "At most 5 savings schemes per invoice")
            List<@Valid @NotNull ChitRedemption> chitRedemptions,

            @Size(max = 10, message = "At most 10 payments per invoice")
            List<@Valid @NotNull Payment> payments,

            @Size(max = 500, message = "Remarks must not exceed 500 characters") String remarks) {

        public List<OldMetalAdjustment> adjustmentsOrEmpty() {
            return oldMetalAdjustments == null ? List.of() : oldMetalAdjustments;
        }

        public List<ChitRedemption> chitsOrEmpty() {
            return chitRedemptions == null ? List.of() : chitRedemptions;
        }

        public List<Payment> paymentsOrEmpty() {
            return payments == null ? List.of() : payments;
        }
    }
}
