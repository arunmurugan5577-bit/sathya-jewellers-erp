package com.jewellery.erp.dashboard.service;

import com.jewellery.erp.category.service.CategoryService;
import com.jewellery.erp.dashboard.dto.DashboardSummaryDto;
import com.jewellery.erp.inventory.service.InventoryItemService;
import com.jewellery.erp.permission.PermissionCatalog;
import com.jewellery.erp.security.SecurityUtils;
import java.math.BigDecimal;
import com.jewellery.erp.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the dashboard.
 *
 * <p>Each metric is gathered only if the signed-in user holds the permission for
 * the module it summarises. That keeps a single permissive endpoint from
 * becoming a side channel: a user who cannot list users must not learn how many
 * exist by reading the dashboard.
 *
 * <p>Adding a card means adding a field, a permission check and a query here -
 * the shape is intentionally boring so that it stays cheap to extend.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final InventoryItemService inventoryItemService;
    private final CategoryService categoryService;
    private final UserRepository userRepository;

    public DashboardService(
            InventoryItemService inventoryItemService,
            CategoryService categoryService,
            UserRepository userRepository) {
        this.inventoryItemService = inventoryItemService;
        this.categoryService = categoryService;
        this.userRepository = userRepository;
    }

    public DashboardSummaryDto summarise() {
        Long totalItems = null;
        Long activeItems = null;
        BigDecimal activeWeight = null;
        Long totalCategories = null;
        Long totalUsers = null;
        Long activeUsers = null;

        if (SecurityUtils.hasPermission(PermissionCatalog.INVENTORY_VIEW)) {
            InventoryItemService.InventorySummary summary = inventoryItemService.summarise();
            totalItems = summary.totalItems();
            activeItems = summary.activeItems();
            activeWeight = summary.activeWeightGrams();
        }
        if (SecurityUtils.hasPermission(PermissionCatalog.CATEGORY_VIEW)) {
            totalCategories = categoryService.countActive();
        }
        if (SecurityUtils.hasPermission(PermissionCatalog.USER_VIEW)) {
            totalUsers = userRepository.count();
            activeUsers = userRepository.countByActiveTrue();
        }

        return new DashboardSummaryDto(
                totalItems, activeItems, activeWeight, totalCategories, totalUsers, activeUsers);
    }
}
