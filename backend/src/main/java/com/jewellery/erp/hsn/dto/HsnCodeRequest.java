package com.jewellery.erp.hsn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Create and update payload for an HSN code.
 *
 * <p>{@code gstPercentage} is a {@link BigDecimal} all the way from the JSON
 * body to the database column. Accepting it as a double here would already have
 * lost precision before any validation ran.
 */
@Schema(name = "HsnCodeRequest")
public record HsnCodeRequest(
        @Schema(example = "7113", description = "4 to 8 digits")
        @NotBlank(message = "HSN code is required")
        @Pattern(regexp = "^[0-9]{4,8}$", message = "HSN code must be 4 to 8 digits")
        String hsnCode,

        @Schema(example = "Articles of jewellery of precious metal")
        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        @Schema(example = "3.00")
        @NotNull(message = "GST percentage is required")
        @DecimalMin(value = "0.00", message = "GST percentage cannot be negative")
        @DecimalMax(value = "100.00", message = "GST percentage cannot exceed 100")
        @Digits(integer = 3, fraction = 2, message = "GST percentage allows at most 2 decimal places")
        BigDecimal gstPercentage,

        @Schema(description = "Defaults to active when omitted")
        Boolean active) {}
