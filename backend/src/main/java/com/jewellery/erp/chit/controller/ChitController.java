package com.jewellery.erp.chit.controller;

import com.jewellery.erp.chit.dto.ChitDtos;
import com.jewellery.erp.chit.entity.ChitSchemeType;
import com.jewellery.erp.chit.entity.ChitStatus;
import com.jewellery.erp.chit.service.ChitService;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.dto.ApiErrorResponse;
import com.jewellery.erp.permission.PermissionCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chits")
@Tag(name = "Chits", description = "Monthly savings towards gold, silver or money")
public class ChitController {

    private final ChitService chitService;

    public ChitController(ChitService chitService) {
        this.chitService = chitService;
    }

    @GetMapping("/schemes")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.CHIT_VIEW + "')")
    @Operation(summary = "The schemes the shop offers",
            description = "Cheapest first. MONEY schemes keep rupees; GOLD schemes buy grams each month. "
                    + "Requires CHIT_VIEW.")
    public ResponseEntity<List<ChitDtos.Scheme>> schemes() {
        return ResponseEntity.ok(chitService.schemes());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.CHIT_VIEW + "')")
    @Operation(summary = "Chits, newest first", description = "Requires CHIT_VIEW.")
    public ResponseEntity<PageResponse<ChitDtos.Summary>> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ChitStatus status,
            @RequestParam(required = false) ChitSchemeType schemeType,
            @RequestParam(required = false) Long customerId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(chitService.findAll(search, status, schemeType, customerId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.CHIT_VIEW + "')")
    @Operation(summary = "One chit, with every instalment", description = "Requires CHIT_VIEW.")
    @ApiResponse(responseCode = "404", description = "Not found",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<ChitDtos.Account> findById(@PathVariable Long id) {
        return ResponseEntity.ok(chitService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + PermissionCatalog.CHIT_CREATE + "')")
    @Operation(summary = "Start a chit",
            description = "The scheme's terms are copied onto the chit, so later changes to the scheme do "
                    + "not alter one already running. Requires CHIT_CREATE.")
    public ResponseEntity<ChitDtos.Account> open(@Valid @RequestBody ChitDtos.OpenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chitService.open(request));
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.CHIT_CREATE + "')")
    @Operation(summary = "Take one instalment",
            description = "The amount is the scheme's, not the caller's. A gold chit needs today's rate and "
                    + "a money chit refuses one. Requires CHIT_CREATE.")
    @ApiResponse(responseCode = "409", description = "The chit takes no more instalments",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<ChitDtos.Account> pay(
            @PathVariable Long id, @Valid @RequestBody ChitDtos.PaymentRequest request) {
        return ResponseEntity.ok(chitService.pay(id, request));
    }

    @PostMapping("/{id}/mature")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.CHIT_EDIT + "')")
    @Operation(summary = "Add the bonus and mature the chit",
            description = "Refused until every instalment the customer owes is in. On a gold chit the bonus "
                    + "buys grams at the rate given. Requires CHIT_EDIT.")
    @ApiResponse(responseCode = "409", description = "Not every instalment is in, or it has already matured",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<ChitDtos.Account> mature(
            @PathVariable Long id, @Valid @RequestBody ChitDtos.MatureRequest request) {
        return ResponseEntity.ok(chitService.mature(id, request));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.CHIT_EDIT + "')")
    @Operation(summary = "Pay the chit out as cash",
            description = "The whole balance goes and the chit closes. A gold chit needs today's rate: what it "
                    + "is worth is what its grams fetch on the day. Requires CHIT_EDIT.")
    @ApiResponse(responseCode = "409", description = "The chit has not matured, or has nothing left on it",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<ChitDtos.Account> withdraw(
            @PathVariable Long id, @Valid @RequestBody ChitDtos.WithdrawRequest request) {
        return ResponseEntity.ok(chitService.withdraw(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.CHIT_DELETE + "')")
    @Operation(summary = "Abandon a chit before it matures",
            description = "The chit is kept, marked CANCELLED, as the record of what was paid in. Any refund "
                    + "is settled at the counter. Requires CHIT_DELETE.")
    public ResponseEntity<ChitDtos.Account> cancel(
            @PathVariable Long id, @Valid @RequestBody ChitDtos.CancelRequest request) {
        return ResponseEntity.ok(chitService.cancel(id, request.reason()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('" + PermissionCatalog.CHIT_DELETE + "')")
    @Operation(summary = "Delete a chit",
            description = "Erases the chit and its instalments. Refused once it has been spent against an "
                    + "invoice. The number is freed when it was the last issued. Requires CHIT_DELETE.")
    public void delete(@PathVariable Long id) {
        chitService.delete(id);
    }
}
