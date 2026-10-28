package com.jewellery.erp.inventory.repository;

import java.math.BigDecimal;

/**
 * One (category, sub category) pair of the stock breakdown, as the database
 * groups it. The service folds these into one entry per category.
 *
 * @param subCategoryId null for pieces filed under no sub category
 */
public record StockGroupRow(
        Long categoryId,
        String categoryName,
        Long subCategoryId,
        String subCategoryName,
        long items,
        BigDecimal weightGrams) {}
