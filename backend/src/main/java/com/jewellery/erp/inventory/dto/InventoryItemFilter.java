package com.jewellery.erp.inventory.dto;

import com.jewellery.erp.inventory.entity.InventoryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Filters for the inventory list. Every one of them is applied in SQL.
 *
 * @param search free text over serial number and description
 * @param serialNumber exact or partial serial match
 * @param active {@code null} means any value of the admin on/off flag
 * @param status {@code null} means available and sold alike
 */
@Schema(name = "InventoryItemFilter")
public record InventoryItemFilter(
        String search,
        String serialNumber,
        Long itemTypeId,
        Long purityId,
        Long categoryId,
        Long subCategoryId,
        Boolean active,
        InventoryStatus status) {}
