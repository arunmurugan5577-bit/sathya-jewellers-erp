package com.jewellery.erp.subcategory.controller;

import com.jewellery.erp.common.dto.ApiErrorResponse;
import com.jewellery.erp.common.dto.LookupDto;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.dto.UpdateStatusRequest;
import com.jewellery.erp.permission.PermissionCatalog;
import com.jewellery.erp.subcategory.dto.SubCategoryDto;
import com.jewellery.erp.subcategory.dto.SubCategoryFilter;
import com.jewellery.erp.subcategory.dto.SubCategoryRequest;
import com.jewellery.erp.subcategory.service.SubCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

/** Sub category master endpoints. */
@RestController
@RequestMapping("/api/sub-categories")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Sub Categories", description = "Subdivisions of a category, e.g. Mens Ring under Ring")
public class SubCategoryController {

    private final SubCategoryService subCategoryService;

    public SubCategoryController(SubCategoryService subCategoryService) {
        this.subCategoryService = subCategoryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SUB_CATEGORY_VIEW + "')")
    @Operation(
            summary = "List sub categories",
            description = "Optionally scoped to one category with ?categoryId=. Requires SUB_CATEGORY_VIEW.")
    public ResponseEntity<PageResponse<SubCategoryDto>> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "Return only sub categories of this category")
            @RequestParam(required = false) Long categoryId,
            @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return ResponseEntity.ok(
                subCategoryService.findAll(new SubCategoryFilter(search, active, categoryId), pageable));
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyAuthority('" + PermissionCatalog.SUB_CATEGORY_VIEW + "','"
            + PermissionCatalog.INVENTORY_VIEW + "')")
    @Operation(
            summary = "Active sub categories of one category",
            description = "Feeds the cascading sub category dropdown on the inventory form. "
                    + "Returns active records only.")
    public ResponseEntity<List<LookupDto>> lookup(
            @Parameter(description = "Parent category", required = true) @RequestParam Long categoryId) {
        return ResponseEntity.ok(subCategoryService.findActiveLookupByCategory(categoryId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SUB_CATEGORY_VIEW + "')")
    @Operation(summary = "Get one sub category")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Found"),
        @ApiResponse(responseCode = "404", description = "No such sub category",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<SubCategoryDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(subCategoryService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SUB_CATEGORY_CREATE + "')")
    @Operation(
            summary = "Create a sub category",
            description = "The parent category is mandatory and must be active. Requires SUB_CATEGORY_CREATE.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created"),
        @ApiResponse(responseCode = "400", description = "Missing or inactive category",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Duplicate name within the category, or duplicate code",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<SubCategoryDto> create(@Valid @RequestBody SubCategoryRequest request) {
        SubCategoryDto created = subCategoryService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/sub-categories/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SUB_CATEGORY_EDIT + "')")
    @Operation(
            summary = "Update a sub category",
            description = "Moving it to another category is refused once inventory references it. "
                    + "Requires SUB_CATEGORY_EDIT.")
    public ResponseEntity<SubCategoryDto> update(
            @PathVariable Long id, @Valid @RequestBody SubCategoryRequest request) {
        return ResponseEntity.ok(subCategoryService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SUB_CATEGORY_EDIT + "')")
    @Operation(summary = "Activate or deactivate", description = "Requires SUB_CATEGORY_EDIT.")
    public ResponseEntity<SubCategoryDto> updateStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(subCategoryService.updateStatus(id, request.active()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SUB_CATEGORY_DELETE + "')")
    @Operation(
            summary = "Delete a sub category",
            description = "Refused while inventory items reference it - deactivate instead. "
                    + "Requires SUB_CATEGORY_DELETE.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "409", description = "Still referenced",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        subCategoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
