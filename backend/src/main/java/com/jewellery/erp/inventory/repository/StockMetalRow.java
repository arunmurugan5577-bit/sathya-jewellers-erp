package com.jewellery.erp.inventory.repository;

import java.math.BigDecimal;

/** What the shop holds of one item type, as the database groups it. */
public record StockMetalRow(Long itemTypeId, String itemTypeName, long items, BigDecimal weightGrams) {}
