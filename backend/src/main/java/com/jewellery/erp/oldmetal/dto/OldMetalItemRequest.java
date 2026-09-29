package com.jewellery.erp.oldmetal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** One row of a purchase bill. The amount is not accepted - the server computes it. */
@Schema(name = "OldMetalItemRequest")
public record OldMetalItemRequest(
        @Schema(example = "1", description = "Item type; must be one of the configured old-metal types")
        @NotNull(message = "Item type is required")
        Long itemTypeId,

        @Schema(description = "Optional; must belong to the item type")
        Long purityId,

        @Schema(example = "Gold coin")
        @NotBlank(message = "Particulars are required")
        @Size(max = 200, message = "Particulars must not exceed 200 characters")
        String particulars,

        @Schema(example = "7108")
        @NotBlank(message = "HSN code is required")
        @Pattern(regexp = "^[0-9]{4,8}$", message = "HSN code must be 4 to 8 digits")
        String hsnCode,

        @Schema(example = "8.000")
        @NotNull(message = "Net weight is required")
        @DecimalMin(value = "0.001", message = "Net weight must be greater than zero")
        @Digits(integer = 9, fraction = 3, message = "Net weight allows at most 3 decimal places")
        BigDecimal netWeightGrams,

        @Schema(description = "Optional - left blank for a coin")
        @DecimalMin(value = "0.001", message = "Gross weight must be greater than zero")
        @Digits(integer = 9, fraction = 3, message = "Gross weight allows at most 3 decimal places")
        BigDecimal grossWeightGrams,

        @Schema(example = "14370")
        @NotNull(message = "Rate is required")
        @DecimalMin(value = "0.01", message = "Rate must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Rate allows at most 2 decimal places")
        BigDecimal ratePerGram) {}
