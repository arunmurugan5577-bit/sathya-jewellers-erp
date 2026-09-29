package com.jewellery.erp.oldmetal.controller;

import com.jewellery.erp.common.dto.ApiErrorResponse;
import com.jewellery.erp.common.dto.CancelRequest;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.dto.RemarksRequest;
import com.jewellery.erp.oldmetal.dto.OldMetalDtos;
import com.jewellery.erp.oldmetal.dto.OldMetalTransactionRequest;
import com.jewellery.erp.oldmetal.entity.OldMetalStatus;
import com.jewellery.erp.oldmetal.service.OldMetalService;
import com.jewellery.erp.permission.PermissionCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/old-metal-transactions")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Old Gold / Silver Purchase", description = "Purchase bills for old metal bought from customers")
public class OldMetalController {

    private final OldMetalService oldMetalService;

    public OldMetalController(OldMetalService oldMetalService) {
        this.oldMetalService = oldMetalService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.OLD_METAL_VIEW + "')")
    @Operation(summary = "List purchase bills", description = "Requires OLD_METAL_VIEW.")
    public ResponseEntity<PageResponse<OldMetalDtos.Summary>> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) OldMetalStatus status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(oldMetalService.findAll(search, status, customerId, from, to, pageable));
    }

    @GetMapping("/usable")
    @PreAuthorize("hasAnyAuthority('" + PermissionCatalog.OLD_METAL_VIEW + "','" + PermissionCatalog.SALES_CREATE + "')")
    @Operation(
            summary = "Bills a customer can still apply to a sale",
            description = "AVAILABLE and PARTIALLY_USED bills with their remaining value. Feeds the old gold / "
                    + "silver section of the sale form. Requires OLD_METAL_VIEW or SALES_CREATE.")
    public ResponseEntity<List<OldMetalDtos.Option>> usable(@RequestParam Long customerId) {
        return ResponseEntity.ok(oldMetalService.findUsableForCustomer(customerId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.OLD_METAL_VIEW + "')")
    @Operation(summary = "Get a purchase bill", description = "Includes everything the printed bill shows, "
            + "with the amount in words. Requires OLD_METAL_VIEW.")
    @ApiResponse(responseCode = "404", description = "OLD_METAL_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<OldMetalDtos.Detail> findById(@PathVariable Long id) {
        return ResponseEntity.ok(oldMetalService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.OLD_METAL_CREATE + "')")
    @Operation(
            summary = "Record an old gold / silver purchase",
            description = "Amounts are computed on the server as net weight x rate, in whole rupees. The bill "
                    + "number is issued by the server. Requires OLD_METAL_CREATE.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created"),
        @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED, CUSTOMER_INACTIVE, "
                + "OLD_METAL_INVALID_ITEM_TYPE", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "CUSTOMER_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<OldMetalDtos.Detail> create(@Valid @RequestBody OldMetalTransactionRequest request) {
        OldMetalDtos.Detail created = oldMetalService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/old-metal-transactions/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PatchMapping("/{id}/remarks")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.OLD_METAL_EDIT + "')")
    @Operation(summary = "Edit remarks", description = "Remarks are the only editable part of an issued bill. "
            + "Requires OLD_METAL_EDIT.")
    public ResponseEntity<OldMetalDtos.Detail> updateRemarks(
            @PathVariable Long id, @Valid @RequestBody RemarksRequest request) {
        return ResponseEntity.ok(oldMetalService.updateRemarks(id, request.remarks()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.OLD_METAL_DELETE + "')")
    @Operation(summary = "Cancel a purchase bill", description = "Allowed only while none of its value has been "
            + "applied to a sale. The bill is kept, marked CANCELLED. Requires OLD_METAL_DELETE.")
    @ApiResponse(responseCode = "409", description = "OLD_METAL_ALREADY_USED",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<OldMetalDtos.Detail> cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request) {
        return ResponseEntity.ok(oldMetalService.cancel(id, request.reason()));
    }
}
