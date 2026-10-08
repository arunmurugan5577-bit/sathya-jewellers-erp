package com.jewellery.erp.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One jewellery piece, with the display name of every master it references so
 * the list screen needs no follow-up calls.
 */
@Schema(name = "InventoryItem")
public record InventoryItemDto(
        Long id,
        @Schema(example = "000123", description = "Exactly 6 digits; leading zeros are significant")
        String serialNumber,
        Long itemTypeId,
        @Schema(example = "Gold") String itemTypeName,
        Long purityId,
        @Schema(example = "22K / 916") String purityName,
        @Schema(example = "916.000") BigDecimal purityValue,
        Long categoryId,
        @Schema(example = "Ring") String categoryName,
        Long subCategoryId,
        @Schema(example = "Mens Ring") String subCategoryName,
        Long hsnId,
        @Schema(example = "7113") String hsnCode,
        @Schema(example = "3.00") BigDecimal gstPercentage,
        @Schema(example = "16") String size,
        @Schema(example = "5.250", description = "Net weight of the piece, or of the whole box, in grams")
        BigDecimal weightGrams,
        @Schema(example = "false", description = "A box sold by weight rather than one article")
        boolean bulk,
        @Schema(example = "5.250", description = "Grams still unsold. Falls invoice by invoice for a bulk box.")
        BigDecimal remainingWeightGrams,
        String description,
        boolean active,
        @Schema(example = "AVAILABLE", description = "AVAILABLE or SOLD") String status,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {}
