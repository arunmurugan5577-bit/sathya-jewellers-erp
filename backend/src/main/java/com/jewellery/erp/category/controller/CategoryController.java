package com.jewellery.erp.category.controller;

import com.jewellery.erp.common.dto.ApiErrorResponse;
import com.jewellery.erp.common.dto.LookupDto;
import com.jewellery.erp.common.dto.MasterFilter;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.dto.UpdateStatusRequest;
import com.jewellery.erp.category.dto.CategoryDto;
import com.jewellery.erp.category.dto.CategoryRequest;
import com.jewellery.erp.category.service.CategoryService;
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

/** Category master endpoints. */
@RestController
@RequestMapping("/api/categories")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Categories", description = "Ring, Chain, Necklace, Bangle, and so on")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.CATEGORY_VIEW + "')")
    @Operation(summary = "List categories", description = "Requires CATEGORY_VIEW.")
    public ResponseEntity<PageResponse<CategoryDto>> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ResponseEntity.ok(categoryService.findAll(new MasterFilter(search, active), pageable));
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyAuthority('" + PermissionCatalog.CATEGORY_VIEW + "','"
            + PermissionCatalog.INVENTORY_VIEW + "','" + PermissionCatalog.SUB_CATEGORY_VIEW + "')")
    @Operation(
            summary = "Active categories for dropdowns",
            description = "Returns active records only. Available to anyone who can view categories, "
                    + "sub categories or inventory, because each of those screens needs the selector.")
    public ResponseEntity<List<LookupDto>> lookup() {
        return ResponseEntity.ok(categoryService.findActiveLookup());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.CATEGORY_VIEW + "')")
    @Operation(summary = "Get one category")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Found"),
        @ApiResponse(responseCode = "404", description = "No such category",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CategoryDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.CATEGORY_CREATE + "')")
    @Operation(summary = "Create a category", description = "Requires CATEGORY_CREATE.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created"),
        @ApiResponse(responseCode = "409", description = "Name or code already exists",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CategoryDto> create(@Valid @RequestBody CategoryRequest request) {
        CategoryDto created = categoryService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/categories/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.CATEGORY_EDIT + "')")
    @Operation(summary = "Update a category", description = "Requires CATEGORY_EDIT.")
    public ResponseEntity<CategoryDto> update(
            @PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.CATEGORY_EDIT + "')")
    @Operation(
            summary = "Activate or deactivate",
            description = "Deactivated categories stay on existing inventory but disappear from dropdowns. "
                    + "Requires CATEGORY_EDIT.")
    public ResponseEntity<CategoryDto> updateStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(categoryService.updateStatus(id, request.active()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.CATEGORY_DELETE + "')")
    @Operation(
            summary = "Delete a category",
            description = "Refused while sub categories or inventory items reference it - deactivate instead. "
                    + "Requires CATEGORY_DELETE.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "409", description = "Still referenced",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
