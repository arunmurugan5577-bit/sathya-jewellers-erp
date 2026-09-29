package com.jewellery.erp.permission.entity;

/**
 * The actions a permission can grant. Mirrors the {@code ck_permissions_action}
 * database check.
 *
 * <p>{@code EXPORT} exists for reports, where "can see the report screen" and "can
 * take the data out of the building as a spreadsheet" are deliberately separate
 * decisions.
 */
public enum PermissionAction {
    VIEW,
    CREATE,
    EDIT,
    DELETE,
    EXPORT
}
