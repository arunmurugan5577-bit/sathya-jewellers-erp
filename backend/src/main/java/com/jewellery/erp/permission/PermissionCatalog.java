package com.jewellery.erp.permission;

import java.util.List;

/**
 * Compile-time constants for every permission code in the system.
 *
 * <p>Because these are {@code static final String} constants, they can be used
 * inside {@code @PreAuthorize} - the compiler folds
 * {@code "hasAuthority('" + ITEM_TYPE_VIEW + "')"} into a single literal. That
 * buys the one thing string literals in annotations never give you: a typo
 * becomes a compile error, and "who checks this permission" is a find-usages
 * away.
 *
 * <p>The catalogue must stay in step with the {@code permissions} table, which
 * is seeded by {@code V2__seed_roles_and_permissions.sql}. Adding a module means
 * adding constants here <em>and</em> a migration - never one without the other.
 */
public final class PermissionCatalog {

    private PermissionCatalog() {}

    // --- Modules -----------------------------------------------------------
    public static final String MODULE_ITEM_TYPE = "ITEM_TYPE";
    public static final String MODULE_CATEGORY = "CATEGORY";
    public static final String MODULE_SUB_CATEGORY = "SUB_CATEGORY";
    public static final String MODULE_HSN = "HSN";
    public static final String MODULE_PURITY = "PURITY";
    public static final String MODULE_USER = "USER";
    public static final String MODULE_INVENTORY = "INVENTORY";
    public static final String MODULE_SHOP_SETTINGS = "SHOP_SETTINGS";
    public static final String MODULE_CUSTOMER = "CUSTOMER";
    public static final String MODULE_OLD_METAL = "OLD_METAL";
    public static final String MODULE_SALES = "SALES";
    public static final String MODULE_LABEL = "LABEL";
    public static final String MODULE_INVENTORY_SERIAL = "INVENTORY_SERIAL";
    public static final String MODULE_WHOLESALE = "WHOLESALE";
    public static final String MODULE_REPORT_WHOLESALE = "REPORT_WHOLESALE";
    public static final String MODULE_REPORT_STOCK = "REPORT_STOCK";
    public static final String MODULE_REPORT_SALES = "REPORT_SALES";

    // --- Item type ---------------------------------------------------------
    public static final String ITEM_TYPE_VIEW = "ITEM_TYPE_VIEW";
    public static final String ITEM_TYPE_CREATE = "ITEM_TYPE_CREATE";
    public static final String ITEM_TYPE_EDIT = "ITEM_TYPE_EDIT";
    public static final String ITEM_TYPE_DELETE = "ITEM_TYPE_DELETE";

    // --- Category ----------------------------------------------------------
    public static final String CATEGORY_VIEW = "CATEGORY_VIEW";
    public static final String CATEGORY_CREATE = "CATEGORY_CREATE";
    public static final String CATEGORY_EDIT = "CATEGORY_EDIT";
    public static final String CATEGORY_DELETE = "CATEGORY_DELETE";

    // --- Sub category ------------------------------------------------------
    public static final String SUB_CATEGORY_VIEW = "SUB_CATEGORY_VIEW";
    public static final String SUB_CATEGORY_CREATE = "SUB_CATEGORY_CREATE";
    public static final String SUB_CATEGORY_EDIT = "SUB_CATEGORY_EDIT";
    public static final String SUB_CATEGORY_DELETE = "SUB_CATEGORY_DELETE";

    // --- HSN ---------------------------------------------------------------
    public static final String HSN_VIEW = "HSN_VIEW";
    public static final String HSN_CREATE = "HSN_CREATE";
    public static final String HSN_EDIT = "HSN_EDIT";
    public static final String HSN_DELETE = "HSN_DELETE";

    // --- Purity ------------------------------------------------------------
    public static final String PURITY_VIEW = "PURITY_VIEW";
    public static final String PURITY_CREATE = "PURITY_CREATE";
    public static final String PURITY_EDIT = "PURITY_EDIT";
    public static final String PURITY_DELETE = "PURITY_DELETE";

    // --- User --------------------------------------------------------------
    public static final String USER_VIEW = "USER_VIEW";
    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_EDIT = "USER_EDIT";
    public static final String USER_DELETE = "USER_DELETE";

    // --- Inventory ---------------------------------------------------------
    public static final String INVENTORY_VIEW = "INVENTORY_VIEW";
    public static final String INVENTORY_CREATE = "INVENTORY_CREATE";
    public static final String INVENTORY_EDIT = "INVENTORY_EDIT";
    public static final String INVENTORY_DELETE = "INVENTORY_DELETE";

    // --- Shop settings -----------------------------------------------------
    public static final String SHOP_SETTINGS_VIEW = "SHOP_SETTINGS_VIEW";
    public static final String SHOP_SETTINGS_EDIT = "SHOP_SETTINGS_EDIT";

    // --- Customer ----------------------------------------------------------
    public static final String CUSTOMER_VIEW = "CUSTOMER_VIEW";
    public static final String CUSTOMER_CREATE = "CUSTOMER_CREATE";
    public static final String CUSTOMER_EDIT = "CUSTOMER_EDIT";
    public static final String CUSTOMER_DELETE = "CUSTOMER_DELETE";

    // --- Old gold / silver purchase ---------------------------------------
    public static final String OLD_METAL_VIEW = "OLD_METAL_VIEW";
    public static final String OLD_METAL_CREATE = "OLD_METAL_CREATE";
    public static final String OLD_METAL_EDIT = "OLD_METAL_EDIT";
    public static final String OLD_METAL_DELETE = "OLD_METAL_DELETE";

    // --- Sales -------------------------------------------------------------
    public static final String SALES_VIEW = "SALES_VIEW";
    public static final String SALES_CREATE = "SALES_CREATE";
    /** Record further payments against a sale's balance, and edit its remarks. */
    public static final String SALES_EDIT = "SALES_EDIT";
    /** Cancel a sale. Completed sales are never physically deleted. */
    public static final String SALES_DELETE = "SALES_DELETE";

    // --- Reports -----------------------------------------------------------
    /** Open label printing and preview labels. */
    public static final String LABEL_VIEW = "LABEL_VIEW";
    /** Send labels to the printer. */
    public static final String LABEL_CREATE = "LABEL_CREATE";
    /** Change the short name and the printer calibration. */
    public static final String LABEL_EDIT = "LABEL_EDIT";

    /** Set where inventory serial numbers start. Administrators only. */
    public static final String INVENTORY_SERIAL_EDIT = "INVENTORY_SERIAL_EDIT";

    // --- Wholesale ---------------------------------------------------------
    /** Open wholesale estimates and party balances. */
    public static final String WHOLESALE_VIEW = "WHOLESALE_VIEW";
    /** Raise a wholesale estimate. */
    public static final String WHOLESALE_CREATE = "WHOLESALE_CREATE";
    /** Cancel a wholesale estimate. */
    public static final String WHOLESALE_DELETE = "WHOLESALE_DELETE";

    public static final String REPORT_WHOLESALE_VIEW = "REPORT_WHOLESALE_VIEW";
    public static final String REPORT_WHOLESALE_EXPORT = "REPORT_WHOLESALE_EXPORT";

    public static final String REPORT_STOCK_VIEW = "REPORT_STOCK_VIEW";
    public static final String REPORT_STOCK_EXPORT = "REPORT_STOCK_EXPORT";
    public static final String REPORT_SALES_VIEW = "REPORT_SALES_VIEW";
    public static final String REPORT_SALES_EXPORT = "REPORT_SALES_EXPORT";

    /**
     * Display order and labels for the permission management screen. Modules not
     * listed here are still returned by the API, just sorted after these.
     */
    public static final List<ModuleDescriptor> MODULES = List.of(
            new ModuleDescriptor(MODULE_SALES, "Sales"),
            new ModuleDescriptor(MODULE_OLD_METAL, "Old Gold / Silver"),
            new ModuleDescriptor(MODULE_CUSTOMER, "Customers"),
            new ModuleDescriptor(MODULE_WHOLESALE, "Wholesale"),
            new ModuleDescriptor(MODULE_INVENTORY_SERIAL, "Serial Numbering"),
            new ModuleDescriptor(MODULE_LABEL, "Label Printing"),
            new ModuleDescriptor(MODULE_REPORT_SALES, "Sales Report"),
            new ModuleDescriptor(MODULE_REPORT_WHOLESALE, "Wholesale Report"),
            new ModuleDescriptor(MODULE_REPORT_STOCK, "Stock Report"),
            new ModuleDescriptor(MODULE_ITEM_TYPE, "Item Types"),
            new ModuleDescriptor(MODULE_CATEGORY, "Categories"),
            new ModuleDescriptor(MODULE_SUB_CATEGORY, "Sub Categories"),
            new ModuleDescriptor(MODULE_HSN, "HSN Codes"),
            new ModuleDescriptor(MODULE_PURITY, "Purities"),
            new ModuleDescriptor(MODULE_INVENTORY, "Inventory"),
            new ModuleDescriptor(MODULE_USER, "Users"),
            new ModuleDescriptor(MODULE_SHOP_SETTINGS, "Shop Settings"));

    /** Human readable label for a module code. */
    public static String labelFor(String module) {
        return MODULES.stream()
                .filter(descriptor -> descriptor.module().equals(module))
                .map(ModuleDescriptor::label)
                .findFirst()
                .orElse(module);
    }

    /** Position of a module in the UI, used to sort the permission matrix. */
    public static int orderOf(String module) {
        for (int index = 0; index < MODULES.size(); index++) {
            if (MODULES.get(index).module().equals(module)) {
                return index;
            }
        }
        return Integer.MAX_VALUE;
    }

    public record ModuleDescriptor(String module, String label) {}
}
