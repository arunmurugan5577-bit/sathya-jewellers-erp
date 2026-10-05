package com.jewellery.erp.wholesale.controller;

import com.jewellery.erp.common.dto.ApiErrorResponse;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.permission.PermissionCatalog;
import com.jewellery.erp.wholesale.dto.WholesaleDtos;
import com.jewellery.erp.wholesale.entity.WholesaleStatus;
import com.jewellery.erp.wholesale.service.WholesaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Wholesale estimates: gold sold by pure weight against a running account.
 *
 * <p>Everything printed is computed here from the pieces in stock. The client
 * sends which pieces and at what touch; it never sends an amount.
 */
@RestController
@RequestMapping("/api/wholesale/estimates")
@Tag(name = "Wholesale", description = "Wholesale estimates and party balances")
public class WholesaleController {

    private final WholesaleService wholesaleService;

    public WholesaleController(WholesaleService wholesaleService) {
        this.wholesaleService = wholesaleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.WHOLESALE_VIEW + "')")
    @Operation(summary = "List wholesale estimates", description = "Requires WHOLESALE_VIEW.")
    public ResponseEntity<PageResponse<WholesaleDtos.Summary>> findAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) WholesaleStatus status,
            @PageableDefault(size = 20, sort = "estimateDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                wholesaleService.findAll(fromDate, toDate, customerId, status, pageable));
    }

    @GetMapping("/item-lookup/{serialNumber}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.WHOLESALE_CREATE + "')")
    @Operation(
            summary = "Look a piece up by serial number",
            description = "Returns the piece's weight and a suggested touch. The barcode on the tag carries "
                    + "this serial, so a scan reaches here. Requires WHOLESALE_CREATE.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "In stock and sellable"),
        @ApiResponse(responseCode = "404", description = "No such serial number",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Already sold",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<WholesaleDtos.ItemLookup> lookupItem(@PathVariable String serialNumber) {
        return ResponseEntity.ok(wholesaleService.lookupItem(serialNumber));
    }

    @GetMapping("/balance/{customerId}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.WHOLESALE_VIEW + "')")
    @Operation(
            summary = "A party's running account",
            description = "Grams of pure gold and the rupee balance. Give a rate to value it. "
                    + "Requires WHOLESALE_VIEW.")
    public ResponseEntity<WholesaleDtos.Balance> balance(
            @PathVariable Long customerId,
            @RequestParam(required = false) BigDecimal pureRatePerGram) {
        return ResponseEntity.ok(wholesaleService.balanceFor(customerId, pureRatePerGram));
    }

    @PostMapping("/calculate")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.WHOLESALE_CREATE + "')")
    @Operation(
            summary = "Price an estimate without saving it",
            description = "Takes no locks and allocates no number, so the form can call it freely. "
                    + "Requires WHOLESALE_CREATE.")
    public ResponseEntity<WholesaleDtos.Calculation> calculate(
            @Valid @RequestBody WholesaleDtos.Request request) {
        return ResponseEntity.ok(wholesaleService.calculate(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.WHOLESALE_VIEW + "')")
    @Operation(summary = "Read one estimate", description = "Requires WHOLESALE_VIEW.")
    public ResponseEntity<WholesaleDtos.Detail> findById(@PathVariable Long id) {
        return ResponseEntity.ok(wholesaleService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.WHOLESALE_CREATE + "')")
    @Operation(
            summary = "Raise a wholesale estimate",
            description = "Marks the pieces sold and moves the party's account on by the pure weight and "
                    + "the making charges. Requires WHOLESALE_CREATE.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Saved"),
        @ApiResponse(responseCode = "409", description = "A piece is already sold",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<WholesaleDtos.Detail> create(@Valid @RequestBody WholesaleDtos.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(wholesaleService.create(request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.WHOLESALE_DELETE + "')")
    @Operation(
            summary = "Cancel an estimate",
            description = "Returns the pieces to stock and reverses exactly what the estimate added to the "
                    + "party's account - not a recomputation, because the rate may have moved since. "
                    + "Requires WHOLESALE_DELETE.")
    public ResponseEntity<WholesaleDtos.Detail> cancel(
            @PathVariable Long id, @Valid @RequestBody WholesaleDtos.CancelRequest request) {
        return ResponseEntity.ok(wholesaleService.cancel(id, request.reason()));
    }
}
