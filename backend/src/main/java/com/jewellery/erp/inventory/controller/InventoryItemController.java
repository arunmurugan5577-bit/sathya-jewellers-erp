package com.jewellery.erp.inventory.controller;

import com.jewellery.erp.common.dto.ApiErrorResponse;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.dto.UpdateStatusRequest;
import com.jewellery.erp.inventory.dto.InventoryItemDto;
import com.jewellery.erp.inventory.dto.InventoryItemFilter;
import com.jewellery.erp.inventory.dto.InventoryItemRequest;
import com.jewellery.erp.inventory.entity.InventoryStatus;
import com.jewellery.erp.inventory.service.InventoryItemService;
import com.jewellery.erp.permission.PermissionCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Inventory endpoints.
 *
 * <p>The list endpoint paginates, sorts and filters in the database. There is
 * deliberately no "fetch everything" variant: a client that downloads the whole
 * stock to filter it in the browser is a client that stops working the year the
 * shop gets busy.
 */
@RestController
@RequestMapping("/api/inventory/items")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Inventory", description = "Individual jewellery pieces")
public class InventoryItemController {

    private final InventoryItemService inventoryItemService;

    public InventoryItemController(InventoryItemService inventoryItemService) {
        this.inventoryItemService = inventoryItemService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.INVENTORY_VIEW + "')")
    @Operation(
            summary = "List inventory items",
            description = "Server-side pagination, sorting and filtering. "
                    + "Example: ?categoryId=1&active=true&page=0&size=20&sort=createdAt,desc. "
                    + "Requires INVENTORY_VIEW.")
    public ResponseEntity<PageResponse<InventoryItemDto>> findAll(
            @Parameter(description = "Free text over serial number, description and size")
            @RequestParam(required = false) String search,
            @Parameter(description = "Full or partial serial number")
            @RequestParam(required = false) String serialNumber,
            @RequestParam(required = false) Long itemTypeId,
            @RequestParam(required = false) Long purityId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long subCategoryId,
            @Parameter(description = "Filter by status; omit for any")
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "AVAILABLE or SOLD; omit for both")
            @RequestParam(required = false) InventoryStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        InventoryItemFilter filter = new InventoryItemFilter(
                search, serialNumber, itemTypeId, purityId, categoryId, subCategoryId, active, status);
        return ResponseEntity.ok(inventoryItemService.findAll(filter, pageable));
    }

    @GetMapping("/next-serial")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.INVENTORY_CREATE + "')")
    @Operation(
            summary = "Suggest the next serial number",
            description = "Returns the next unused 6 digit serial, zero padded. This is a suggestion and not a "
                    + "reservation - the uniqueness check at save time is what actually guarantees it. "
                    + "Requires INVENTORY_CREATE.")
    public ResponseEntity<Map<String, String>> nextSerialNumber() {
        return ResponseEntity.ok(Map.of("serialNumber", inventoryItemService.suggestNextSerialNumber()));
    }

    @GetMapping("/by-serial/{serialNumber}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.INVENTORY_VIEW + "')")
    @Operation(
            summary = "Find a piece by its serial number",
            description = "The counter lookup. Requires INVENTORY_VIEW.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Found"),
        @ApiResponse(responseCode = "404", description = "No piece with that serial number",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<InventoryItemDto> findBySerialNumber(@PathVariable String serialNumber) {
        return ResponseEntity.ok(inventoryItemService.findBySerialNumber(serialNumber));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.INVENTORY_VIEW + "')")
    @Operation(summary = "Get one inventory item")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Found"),
        @ApiResponse(responseCode = "404", description = "No such item",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<InventoryItemDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryItemService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.INVENTORY_CREATE + "')")
    @Operation(
            summary = "Add an inventory item",
            description = "The purity must belong to the selected item type and the sub category to the "
                    + "selected category; both are verified server-side. Requires INVENTORY_CREATE.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created"),
        @ApiResponse(responseCode = "400", description = "Validation failed, or a reference is inactive "
                + "or belongs to the wrong parent",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "The serial number already exists",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<InventoryItemDto> create(@Valid @RequestBody InventoryItemRequest request) {
        InventoryItemDto created = inventoryItemService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/inventory/items/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.INVENTORY_EDIT + "')")
    @Operation(summary = "Update an inventory item", description = "Requires INVENTORY_EDIT.")
    public ResponseEntity<InventoryItemDto> update(
            @PathVariable Long id, @Valid @RequestBody InventoryItemRequest request) {
        return ResponseEntity.ok(inventoryItemService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.INVENTORY_EDIT + "')")
    @Operation(
            summary = "Activate or deactivate an item",
            description = "The normal way a piece leaves the active stock list. Requires INVENTORY_EDIT.")
    public ResponseEntity<InventoryItemDto> updateStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(inventoryItemService.updateStatus(id, request.active()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.INVENTORY_DELETE + "')")
    @Operation(
            summary = "Delete an inventory item",
            description = "For correcting a mis-keyed entry. Prefer deactivation for a piece that physically "
                    + "existed. Requires INVENTORY_DELETE.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        inventoryItemService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
