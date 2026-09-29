package com.jewellery.erp.hsn.controller;

import com.jewellery.erp.common.dto.ApiErrorResponse;
import com.jewellery.erp.common.dto.LookupDto;
import com.jewellery.erp.common.dto.MasterFilter;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.dto.UpdateStatusRequest;
import com.jewellery.erp.hsn.dto.HsnCodeDto;
import com.jewellery.erp.hsn.dto.HsnCodeRequest;
import com.jewellery.erp.hsn.service.HsnCodeService;
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

/** HSN code master endpoints. */
@RestController
@RequestMapping("/api/hsn-codes")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "HSN Codes", description = "HSN codes and their GST rates")
public class HsnCodeController {

    private final HsnCodeService hsnCodeService;

    public HsnCodeController(HsnCodeService hsnCodeService) {
        this.hsnCodeService = hsnCodeService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.HSN_VIEW + "')")
    @Operation(summary = "List HSN codes", description = "Requires HSN_VIEW.")
    public ResponseEntity<PageResponse<HsnCodeDto>> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @ParameterObject @PageableDefault(size = 20, sort = "hsnCode", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ResponseEntity.ok(hsnCodeService.findAll(new MasterFilter(search, active), pageable));
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyAuthority('" + PermissionCatalog.HSN_VIEW + "','"
            + PermissionCatalog.INVENTORY_VIEW + "')")
    @Operation(
            summary = "Active HSN codes for dropdowns",
            description = "Returns active records only. Available to anyone who can view HSN codes "
                    + "or inventory, because the inventory form needs the selector.")
    public ResponseEntity<List<LookupDto>> lookup() {
        return ResponseEntity.ok(hsnCodeService.findActiveLookup());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.HSN_VIEW + "')")
    @Operation(summary = "Get one HSN code")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Found"),
        @ApiResponse(responseCode = "404", description = "No such HSN code",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<HsnCodeDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(hsnCodeService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.HSN_CREATE + "')")
    @Operation(summary = "Create an HSN code", description = "Requires HSN_CREATE.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created"),
        @ApiResponse(responseCode = "409", description = "This HSN code already exists",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<HsnCodeDto> create(@Valid @RequestBody HsnCodeRequest request) {
        HsnCodeDto created = hsnCodeService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/hsn-codes/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.HSN_EDIT + "')")
    @Operation(summary = "Update an HSN code", description = "Requires HSN_EDIT.")
    public ResponseEntity<HsnCodeDto> update(
            @PathVariable Long id, @Valid @RequestBody HsnCodeRequest request) {
        return ResponseEntity.ok(hsnCodeService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.HSN_EDIT + "')")
    @Operation(
            summary = "Activate or deactivate",
            description = "Deactivated HSN codes stay on existing inventory but disappear from dropdowns. "
                    + "Requires HSN_EDIT.")
    public ResponseEntity<HsnCodeDto> updateStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(hsnCodeService.updateStatus(id, request.active()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.HSN_DELETE + "')")
    @Operation(
            summary = "Delete an HSN code",
            description = "Refused while inventory items reference it - deactivate instead. "
                    + "Requires HSN_DELETE.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "409", description = "Still referenced",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        hsnCodeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
