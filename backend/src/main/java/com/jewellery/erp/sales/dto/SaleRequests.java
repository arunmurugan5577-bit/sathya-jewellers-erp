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

    @Schema(name = "SaleItemRequest")
    public record Item(
            @Schema(example = "000123")
            @NotBlank(message = "Serial number is required")
            @Pattern(regexp = "^\\s*\\d{1,6}\\s*$", message = "Serial number must be up to six digits")
            String serialNumber,

            @Schema(description = "Printed description. Defaults to the piece's category and sub category.",
                    example = "Ladies Ring")
            @Size(max = 200, message = "Particulars must not exceed 200 characters")
            String particulars,

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

            @Size(max = 10, message = "At most 10 payments per invoice")
            List<@Valid @NotNull Payment> payments,

            @Size(max = 500, message = "Remarks must not exceed 500 characters") String remarks) {

        public List<OldMetalAdjustment> adjustmentsOrEmpty() {
            return oldMetalAdjustments == null ? List.of() : oldMetalAdjustments;
        }

        public List<Payment> paymentsOrEmpty() {
            return payments == null ? List.of() : payments;
        }
    }
}
