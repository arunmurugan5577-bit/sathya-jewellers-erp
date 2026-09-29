package com.jewellery.erp.purity.controller;

import com.jewellery.erp.common.dto.ApiErrorResponse;
import com.jewellery.erp.common.dto.LookupDto;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.dto.UpdateStatusRequest;
import com.jewellery.erp.permission.PermissionCatalog;
import com.jewellery.erp.purity.dto.PurityDto;
import com.jewellery.erp.purity.dto.PurityFilter;
import com.jewellery.erp.purity.dto.PurityRequest;
import com.jewellery.erp.purity.service.PurityService;
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

/** Purity master endpoints. */
@RestController
@RequestMapping("/api/purities")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Purities", description = "Fineness values, each belonging to one item type")
public class PurityController {

    private final PurityService purityService;

    public PurityController(PurityService purityService) {
        this.purityService = purityService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.PURITY_VIEW + "')")
    @Operation(
            summary = "List purities",
            description = "Optionally scoped to one item type with ?itemTypeId=. Requires PURITY_VIEW.")
    public ResponseEntity<PageResponse<PurityDto>> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "Return only purities of this item type")
            @RequestParam(required = false) Long itemTypeId,
            @ParameterObject @PageableDefault(size = 20, sort = "purityValue", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(purityService.findAll(new PurityFilter(search, active, itemTypeId), pageable));
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyAuthority('" + PermissionCatalog.PURITY_VIEW + "','"
            + PermissionCatalog.INVENTORY_VIEW + "')")
    @Operation(
            summary = "Active purities of one item type",
            description = "Feeds the cascading purity dropdown on the inventory form. "
                    + "Returns active records only.")
    public ResponseEntity<List<LookupDto>> lookup(
            @Parameter(description = "Owning item type", required = true) @RequestParam Long itemTypeId) {
        return ResponseEntity.ok(purityService.findActiveLookupByItemType(itemTypeId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.PURITY_VIEW + "')")
    @Operation(summary = "Get one purity")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Found"),
        @ApiResponse(responseCode = "404", description = "No such purity",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PurityDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(purityService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.PURITY_CREATE + "')")
    @Operation(
            summary = "Create a purity",
            description = "The item type is mandatory and must be active. Requires PURITY_CREATE.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created"),
        @ApiResponse(responseCode = "409", description = "Duplicate name or value within the item type",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PurityDto> create(@Valid @RequestBody PurityRequest request) {
        PurityDto created = purityService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/purities/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.PURITY_EDIT + "')")
    @Operation(
            summary = "Update a purity",
            description = "Moving it to another item type is refused once inventory references it. "
                    + "Requires PURITY_EDIT.")
    public ResponseEntity<PurityDto> update(
            @PathVariable Long id, @Valid @RequestBody PurityRequest request) {
        return ResponseEntity.ok(purityService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.PURITY_EDIT + "')")
    @Operation(summary = "Activate or deactivate", description = "Requires PURITY_EDIT.")
    public ResponseEntity<PurityDto> updateStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(purityService.updateStatus(id, request.active()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.PURITY_DELETE + "')")
    @Operation(
            summary = "Delete a purity",
            description = "Refused while inventory items reference it - deactivate instead. "
                    + "Requires PURITY_DELETE.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "409", description = "Still referenced",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        purityService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
