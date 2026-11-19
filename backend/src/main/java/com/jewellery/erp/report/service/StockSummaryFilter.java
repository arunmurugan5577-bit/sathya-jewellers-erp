package com.jewellery.erp.report.service;

/**
 * What to count on the stock sheet.
 *
 * <p>No dates. The sheet is what is on the shelf now - active and unsold - so
 * a period would only invite the question of which one.
 */
public record StockSummaryFilter(Long itemTypeId, Long categoryId, Long subCategoryId) {}
