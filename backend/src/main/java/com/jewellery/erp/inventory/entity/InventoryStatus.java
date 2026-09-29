package com.jewellery.erp.inventory.entity;

/**
 * Whether a physical piece is still in the shop.
 *
 * <p>Independent of {@code active}, which is an administrative on/off switch. A
 * piece can be sold only while it is both {@code AVAILABLE} and active.
 */
public enum InventoryStatus {
    AVAILABLE,
    SOLD
}
