package com.jewellery.erp.purity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Create and update payload for a purity.
 *
 * <p>A purity always belongs to an item type, which is what makes the cascading
 * dropdown on the inventory form possible without hardcoding which finenesses
 * apply to which metal.
 */
@Schema(name = "PurityRequest")
public record PurityRequest(
        @Schema(example = "1", description = "Owning item type; mandatory")
        @NotNull(message = "Item type is required")
        Long itemTypeId,

        @Schema(example = "22K / 916")
        @NotBlank(message = "Name is required")
        @Size(max = 50, message = "Name must not exceed 50 characters")
        String name,

        @Schema(example = "916.000", description = "Parts per thousand, up to 3 decimal places")
        @NotNull(message = "Purity value is required")
        @DecimalMin(value = "0.001", message = "Purity value must be greater than zero")
        @DecimalMax(value = "999.999", message = "Purity value cannot exceed 999.999")
        @Digits(integer = 3, fraction = 3, message = "Purity value allows at most 3 decimal places")
        BigDecimal purityValue,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        @Schema(description = "Defaults to active when omitted")
        Boolean active) {}
