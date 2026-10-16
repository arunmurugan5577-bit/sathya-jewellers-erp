package com.jewellery.erp.chit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** What the chit screens send and receive. */
public final class ChitDtos {

    private ChitDtos() {}

    // ------------------------------------------------------------- schemes ---

    @Schema(name = "ChitScheme")
    public record Scheme(
            Long id,
            @Schema(example = "G3000") String code,
            @Schema(example = "Gold 3,000 a month") String name,
            @Schema(example = "GOLD", description = "MONEY keeps rupees; GOLD buys grams each month")
            String schemeType,
            @Schema(example = "3000.00") BigDecimal monthlyAmount,
            @Schema(example = "11", description = "What the customer pays") short payableInstalments,
            @Schema(example = "12", description = "What the chit is worth") short totalInstalments,
            @Schema(example = "36000.00", description = "monthlyAmount x totalInstalments")
            BigDecimal maturityAmount,
            boolean active) {}

    // ------------------------------------------------------------ payments ---

    @Schema(name = "ChitPayment")
    public record Payment(
            Long id,
            @Schema(example = "3") short instalmentNo,
            LocalDate paidOn,
            @Schema(example = "3000.00") BigDecimal amount,
            @Schema(example = "13675.00", description = "Gold chits only") BigDecimal ratePerGram,
            @Schema(example = "0.219", description = "Gold chits only") BigDecimal grams,
            @Schema(description = "The bonus month, which the shop puts in") boolean paidByShop,
            String paymentMethod,
            String referenceNo,
            String remarks,
            Instant createdAt,
            String createdBy) {}

    // ---------------------------------------------------------- redemptions ---

    @Schema(name = "ChitRedemption")
    public record Redemption(
            Long id,
            LocalDate redeemedOn,
            @Schema(example = "SALE", description = "CASH if the customer took the money, SALE if they bought")
            String kind,
            @Schema(example = "2.632", description = "Gold chits only") BigDecimal grams,
            @Schema(example = "14800.00", description = "Gold chits only: what the grams were valued at")
            BigDecimal ratePerGram,
            @Schema(example = "38953.00") BigDecimal amount,
            Long saleId,
            @Schema(example = "INV-2026-000012") String invoiceNumber,
            @Schema(example = "ACTIVE", description = "CANCELLED once the invoice it was applied to is cancelled")
            String status,
            String paymentMethod,
            String referenceNo,
            String remarks,
            Instant createdAt,
            String createdBy) {}

    // ------------------------------------------------------------- the chit ---

    @Schema(name = "ChitAccount")
    public record Account(
            Long id,
            @Schema(example = "CH-2026-000001") String chitNumber,
            Long customerId,
            String customerCode,
            String customerName,
            String customerMobile,
            Long schemeId,
            String schemeCode,
            String schemeName,
            @Schema(example = "GOLD") String schemeType,
            @Schema(example = "3000.00") BigDecimal monthlyAmount,
            short payableInstalments,
            short totalInstalments,
            LocalDate startedOn,
            @Schema(example = "ACTIVE") String status,
            short paidInstalments,
            @Schema(description = "Months the customer still owes. The bonus is not theirs to pay.")
            short instalmentsOwed,
            @Schema(example = "33000.00", description = "Rupees put in, the bonus included once matured")
            BigDecimal paidAmount,
            @Schema(example = "2.632", description = "Grams bought. Gold chits only.")
            BigDecimal accruedGrams,
            @Schema(example = "36000.00", description = "Rupees still unspent") BigDecimal balanceAmount,
            @Schema(example = "2.632", description = "Grams still unspent") BigDecimal balanceGrams,
            @Schema(example = "36000.00", description = "What the chit comes to if every instalment is paid")
            BigDecimal maturityAmount,
            @Schema(example = "6000.00", description = "What the customer has still to pay in")
            BigDecimal remainingToPay,
            LocalDate maturedOn,
            LocalDate closedOn,
            String closeReason,
            String remarks,
            List<Payment> payments,
            @Schema(description = "What has been taken off the chit: cash withdrawals and invoices")
            List<Redemption> redemptions,
            Instant createdAt,
            String createdBy) {}

    @Schema(name = "ChitAccountSummary")
    public record Summary(
            Long id,
            String chitNumber,
            String customerName,
            String customerMobile,
            String schemeName,
            String schemeType,
            BigDecimal monthlyAmount,
            LocalDate startedOn,
            String status,
            short paidInstalments,
            short payableInstalments,
            BigDecimal paidAmount,
            BigDecimal accruedGrams,
            BigDecimal balanceAmount,
            BigDecimal balanceGrams) {}

    // ------------------------------------------------------------- requests ---

    @Schema(name = "OpenChitRequest")
    public record OpenRequest(
            @NotNull(message = "Choose the customer") Long customerId,
            @NotNull(message = "Choose the scheme") Long schemeId,
            @Schema(description = "Defaults to today. Cannot be in the future.") LocalDate startedOn,
            @Size(max = 500, message = "Remarks must not exceed 500 characters") String remarks) {}

    /**
     * One instalment.
     *
     * <p>{@code ratePerGram} is required on a gold chit and refused on a money
     * one - the server knows which from the chit, so the counter cannot be
     * asked for a rate that means nothing.
     */
    @Schema(name = "ChitPaymentRequest")
    public record PaymentRequest(
            @Schema(description = "Defaults to today.") LocalDate paidOn,

            @Schema(example = "13675.00", description = "The gold rate today. Gold chits only.")
            @DecimalMin(value = "0.01", message = "The rate must be more than zero")
            @Digits(integer = 12, fraction = 2, message = "The rate allows up to 2 decimal places")
            BigDecimal ratePerGram,

            @Schema(example = "CASH") String paymentMethod,

            @Size(max = 100, message = "Reference must not exceed 100 characters") String referenceNo,

            @Size(max = 300, message = "Remarks must not exceed 300 characters") String remarks) {}

    /** Adding the bonus. A gold chit needs the rate it buys at. */
    @Schema(name = "MatureChitRequest")
    public record MatureRequest(
            @Schema(description = "Defaults to today.") LocalDate maturedOn,

            @Schema(example = "14500.00", description = "The gold rate today. Gold chits only.")
            @DecimalMin(value = "0.01", message = "The rate must be more than zero")
            @Digits(integer = 12, fraction = 2, message = "The rate allows up to 2 decimal places")
            BigDecimal ratePerGram) {}

    /**
     * Taking the chit as cash.
     *
     * <p>The whole balance goes: a withdrawal is the customer closing the chit
     * and walking out with the money. A gold chit needs the rate today, since
     * what it is worth is what its grams fetch on the day it is cashed.
     */
    @Schema(name = "WithdrawChitRequest")
    public record WithdrawRequest(
            @Schema(description = "Defaults to today.") LocalDate withdrawnOn,

            @Schema(example = "14800.00", description = "The gold rate today. Gold chits only.")
            @DecimalMin(value = "0.01", message = "The rate must be more than zero")
            @Digits(integer = 12, fraction = 2, message = "The rate allows up to 2 decimal places")
            BigDecimal ratePerGram,

            @Schema(example = "CASH") String paymentMethod,

            @Size(max = 100, message = "Reference must not exceed 100 characters") String referenceNo,

            @Size(max = 300, message = "Remarks must not exceed 300 characters") String remarks) {}

    @Schema(name = "CancelChitRequest")
    public record CancelRequest(
            @Size(max = 500, message = "Reason must not exceed 500 characters") String reason) {}
}
