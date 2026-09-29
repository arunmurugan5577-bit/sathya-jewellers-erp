package com.jewellery.erp.itemtype.controller;

import com.jewellery.erp.common.dto.ApiErrorResponse;
import com.jewellery.erp.common.dto.LookupDto;
import com.jewellery.erp.common.dto.MasterFilter;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.dto.UpdateStatusRequest;
import com.jewellery.erp.itemtype.dto.ItemTypeDto;
import com.jewellery.erp.itemtype.dto.ItemTypeRequest;
import com.jewellery.erp.itemtype.service.ItemTypeService;
import com.jewellery.erp.permission.PermissionCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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

/** Item type master endpoints. */
@RestController
@RequestMapping("/api/item-types")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Item Types", description = "Gold, Silver, Platinum, Diamond")
public class ItemTypeController {

    private final ItemTypeService itemTypeService;

    public ItemTypeController(ItemTypeService itemTypeService) {
        this.itemTypeService = itemTypeService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.ITEM_TYPE_VIEW + "')")
    @Operation(summary = "List item types", description = "Requires ITEM_TYPE_VIEW.")
    public ResponseEntity<PageResponse<ItemTypeDto>> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ResponseEntity.ok(itemTypeService.findAll(new MasterFilter(search, active), pageable));
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyAuthority('" + PermissionCatalog.ITEM_TYPE_VIEW + "','"
            + PermissionCatalog.INVENTORY_VIEW + "','" + PermissionCatalog.PURITY_VIEW + "')")
    @Operation(
            summary = "Active item types for dropdowns",
            description = "Returns active records only. Available to anyone who can view item types, "
                    + "purities or inventory, because each of those screens needs the selector.")
    public ResponseEntity<List<LookupDto>> lookup() {
        return ResponseEntity.ok(itemTypeService.findActiveLookup());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.ITEM_TYPE_VIEW + "')")
    @Operation(summary = "Get one item type")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Found"),
        @ApiResponse(responseCode = "404", description = "No such item type",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ItemTypeDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(itemTypeService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.ITEM_TYPE_CREATE + "')")
    @Operation(summary = "Create an item type", description = "Requires ITEM_TYPE_CREATE.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created"),
        @ApiResponse(responseCode = "409", description = "Name or code already exists",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ItemTypeDto> create(@Valid @RequestBody ItemTypeRequest request) {
        ItemTypeDto created = itemTypeService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/item-types/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.ITEM_TYPE_EDIT + "')")
    @Operation(summary = "Update an item type", description = "Requires ITEM_TYPE_EDIT.")
    public ResponseEntity<ItemTypeDto> update(
            @PathVariable Long id, @Valid @RequestBody ItemTypeRequest request) {
        return ResponseEntity.ok(itemTypeService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.ITEM_TYPE_EDIT + "')")
    @Operation(
            summary = "Activate or deactivate",
            description = "Deactivated item types stay on existing inventory but disappear from dropdowns. "
                    + "Requires ITEM_TYPE_EDIT.")
    public ResponseEntity<ItemTypeDto> updateStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(itemTypeService.updateStatus(id, request.active()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.ITEM_TYPE_DELETE + "')")
    @Operation(
            summary = "Delete an item type",
            description = "Refused while purities or inventory items reference it - deactivate instead. "
                    + "Requires ITEM_TYPE_DELETE.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "409", description = "Still referenced",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        itemTypeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
