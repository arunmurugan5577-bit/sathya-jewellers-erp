package com.jewellery.erp.inventory.dto;

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
 * Create and update payload for a jewellery piece.
 *
 * <p>The validation here is the first of three layers, not the only one: the
 * service re-checks the cross-field rules (purity belongs to item type, sub
 * category belongs to category, serial number unused) and the database enforces
 * the format, the uniqueness and the positive weight. Each layer exists because
 * the one above it can be bypassed.
 */
@Schema(name = "InventoryItemRequest")
public record InventoryItemRequest(
        // Ignored when adding a piece: the counter issues the number so two
        // pieces cannot end up with the same barcode. Still required when
        // editing, where it identifies the piece already on the shelf.
        @Schema(example = "001",
                description = "Three to six digits. Assigned by the server when adding; required when editing.")
        @Pattern(regexp = "^[0-9]{3,6}$", message = "Serial number must be 3 to 6 digits")
        String serialNumber,

        @Schema(example = "1")
        @NotNull(message = "Item type is required")
        Long itemTypeId,

        @Schema(example = "2", description = "Must belong to the selected item type")
        @NotNull(message = "Purity is required")
        Long purityId,

        @Schema(example = "1")
        @NotNull(message = "Category is required")
        Long categoryId,

        @Schema(example = "3", description = "Optional; must belong to the selected category")
        Long subCategoryId,

        @Schema(example = "1", description = "Optional at stock-in; required before billing")
        Long hsnId,

        @Schema(example = "16", description = "Free text - a ring size, a length, or a label")
        @Size(max = 50, message = "Size must not exceed 50 characters")
        String size,

        @Schema(example = "5.250", description = "Gross weight in grams, up to 3 decimal places")
        @NotNull(message = "Weight is required")
        @DecimalMin(value = "0.001", message = "Weight must be greater than zero")
        @DecimalMax(value = "999999999.999", message = "Weight is out of range")
        @Digits(integer = 9, fraction = 3, message = "Weight allows at most 3 decimal places")
        BigDecimal weightGrams,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        @Schema(description = "Defaults to active when omitted")
        Boolean active) {}
