package com.jewellery.erp.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * What the shop is holding of one item type - gold, silver, and whatever else
 * it files pieces under.
 *
 * <p>The same stock the dashboard's own card counts: active, unsold, weighed
 * by what is left of it.
 */
@Schema(name = "StockByItemType")
public record StockByItemTypeDto(
        Long itemTypeId,
        @Schema(example = "Gold") String itemTypeName,
        @Schema(example = "10") long items,
        @Schema(example = "249.520", description = "Grams on the shelf") BigDecimal weightGrams) {}
