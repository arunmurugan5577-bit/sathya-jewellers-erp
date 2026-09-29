package com.jewellery.erp.shop.controller;

import com.jewellery.erp.common.dto.ApiErrorResponse;
import com.jewellery.erp.permission.PermissionCatalog;
import com.jewellery.erp.shop.dto.ShopSettingsDto;
import com.jewellery.erp.shop.dto.ShopSettingsRequest;
import com.jewellery.erp.shop.service.ShopSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shop profile endpoints.
 *
 * <p>There is no POST and no DELETE: the shop always exists, so the only verbs
 * that mean anything are read and update.
 */
@RestController
@RequestMapping("/api/shop-settings")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Shop Settings", description = "Shop profile used on invoices, receipts and GST reports")
public class ShopSettingsController {

    private final ShopSettingsService shopSettingsService;

    public ShopSettingsController(ShopSettingsService shopSettingsService) {
        this.shopSettingsService = shopSettingsService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SHOP_SETTINGS_VIEW + "')")
    @Operation(summary = "Read the shop profile", description = "Requires SHOP_SETTINGS_VIEW.")
    public ResponseEntity<ShopSettingsDto> find() {
        return ResponseEntity.ok(shopSettingsService.find());
    }

    /**
     * The shop name alone, for the application chrome.
     *
     * <p>Separate from the full profile and open to any signed-in user, because
     * the name of the shop you work at is branding rather than protected data -
     * gating it behind SHOP_SETTINGS_VIEW left staff looking at a generic
     * product name in their own shop's software. Nothing else from the profile
     * is exposed here; the GSTIN and contact details still require the
     * permission.
     */
    @GetMapping("/branding")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "The shop name, for the UI chrome",
            description = "Available to any signed-in user. Returns the name only.")
    public ResponseEntity<Map<String, String>> branding() {
        return ResponseEntity.ok(Map.of("shopName", shopSettingsService.shopName()));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SHOP_SETTINGS_EDIT + "')")
    @Operation(
            summary = "Update the shop profile",
            description = "GSTIN and pincode formats are validated. Requires SHOP_SETTINGS_EDIT.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Updated"),
        @ApiResponse(responseCode = "400", description = "Validation failed",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ShopSettingsDto> update(@Valid @RequestBody ShopSettingsRequest request) {
        return ResponseEntity.ok(shopSettingsService.update(request));
    }
}
