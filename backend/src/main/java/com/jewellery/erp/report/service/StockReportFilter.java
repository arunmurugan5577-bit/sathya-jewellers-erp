package com.jewellery.erp.report.service;

import com.jewellery.erp.inventory.entity.InventoryStatus;
import java.time.LocalDate;

/** Pieces stocked in between two dates (inclusive, shop time zone). */
public record StockReportFilter(
        LocalDate startDate,
        LocalDate endDate,
        Long itemTypeId,
        Long categoryId,
        InventoryStatus status,
        boolean includeInactive) {}
