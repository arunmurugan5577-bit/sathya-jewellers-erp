package com.jewellery.erp.sales.controller;

import com.jewellery.erp.common.dto.ApiErrorResponse;
import com.jewellery.erp.common.dto.CancelRequest;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.dto.RemarksRequest;
import com.jewellery.erp.permission.PermissionCatalog;
import com.jewellery.erp.sales.dto.SaleDtos;
import com.jewellery.erp.sales.dto.SaleRequests;
import com.jewellery.erp.sales.entity.PaymentStatus;
import com.jewellery.erp.sales.entity.SaleStatus;
import com.jewellery.erp.sales.service.SaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
@RequestMapping("/api/sales")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Sales", description = "Tax invoices")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SALES_VIEW + "')")
    @Operation(summary = "List invoices", description = "Requires SALES_VIEW.")
    public ResponseEntity<PageResponse<SaleDtos.Summary>> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SaleStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(saleService.findAll(search, status, paymentStatus, customerId, from, to, pageable));
    }

    @GetMapping("/item-lookup/{serialNumber}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SALES_CREATE + "')")
    @Operation(
            summary = "Look up a piece by serial number",
            description = "Returns category, sub category, HSN / GST rate and net weight so the counter can fill "
                    + "the line. Fails if the piece is sold, inactive or has no HSN code. Requires SALES_CREATE.")
    @ApiResponses({
        @ApiResponse(responseCode = "404", description = "INVENTORY_ITEM_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "ITEM_ALREADY_SOLD",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<SaleDtos.ItemLookup> lookupItem(@PathVariable String serialNumber) {
        return ResponseEntity.ok(saleService.lookupItem(serialNumber));
    }

    @PostMapping("/calculate")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SALES_CREATE + "')")
    @Operation(summary = "Preview an invoice",
            description = "Computes every figure without saving anything. Requires SALES_CREATE.")
    public ResponseEntity<SaleDtos.Calculation> calculate(@Valid @RequestBody SaleRequests.Sale request) {
        return ResponseEntity.ok(saleService.calculate(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SALES_VIEW + "')")
    @Operation(summary = "Get an invoice", description = "Everything the printed tax invoice shows. Requires SALES_VIEW.")
    @ApiResponse(responseCode = "404", description = "INVOICE_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<SaleDtos.Detail> findById(@PathVariable Long id) {
        return ResponseEntity.ok(saleService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SALES_CREATE + "')")
    @Operation(
            summary = "Complete a sale",
            description = "Locks the pieces and any old gold / silver bills, computes the invoice, marks the "
                    + "pieces SOLD and issues the next invoice number, all in one transaction. "
                    + "Requires SALES_CREATE.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created"),
        @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED, DISCOUNT_EXCEEDS_TOTAL, "
                + "PAYMENT_EXCEEDS_BALANCE, OLD_METAL_CUSTOMER_MISMATCH",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "ITEM_ALREADY_SOLD, OLD_METAL_ALREADY_USED, "
                + "OLD_METAL_AMOUNT_EXCEEDED",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<SaleDtos.Detail> create(@Valid @RequestBody SaleRequests.Sale request) {
        SaleDtos.Detail created = saleService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/sales/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SALES_EDIT + "')")
    @Operation(summary = "Record a further payment", description = "Cannot exceed the balance. Requires SALES_EDIT.")
    public ResponseEntity<SaleDtos.Detail> addPayment(
            @PathVariable Long id, @Valid @RequestBody SaleRequests.Payment request) {
        return ResponseEntity.ok(saleService.addPayment(id, request));
    }

    @PatchMapping("/{id}/remarks")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SALES_EDIT + "')")
    @Operation(summary = "Edit remarks", description = "Requires SALES_EDIT.")
    public ResponseEntity<SaleDtos.Detail> updateRemarks(
            @PathVariable Long id, @Valid @RequestBody RemarksRequest request) {
        return ResponseEntity.ok(saleService.updateRemarks(id, request.remarks()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.SALES_DELETE + "')")
    @Operation(summary = "Cancel an invoice",
            description = "Returns the pieces to stock and old gold / silver value to its bills. The invoice is "
                    + "kept, marked CANCELLED. Requires SALES_DELETE.")
    @ApiResponse(responseCode = "409", description = "SALE_ALREADY_CANCELLED",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<SaleDtos.Detail> cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest request) {
        return ResponseEntity.ok(saleService.cancel(id, request.reason()));
    }
}
