package com.jewellery.erp.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * What the shop is holding, by category and the sub categories under it.
 *
 * <p>"Holding" means the same thing here as on the dashboard's own stock card:
 * active pieces that have not been sold, weighed by what is left of them, so a
 * bulk box half sold counts the half still on the shelf.
 */
@Schema(name = "StockByCategory")
public record StockByCategoryDto(
        Long categoryId,
        @Schema(example = "Bangle") String categoryName,
        @Schema(example = "14") long items,
        @Schema(example = "251.080", description = "Grams on the shelf") BigDecimal weightGrams,
        @Schema(description = "The sub categories under it; a piece with none is listed as Unspecified")
        List<SubCategoryStock> subCategories) {

    @Schema(name = "StockBySubCategory")
    public record SubCategoryStock(
            @Schema(description = "Null for pieces filed under no sub category") Long subCategoryId,
            @Schema(example = "Kids Ring") String subCategoryName,
            long items,
            BigDecimal weightGrams) {}
}
