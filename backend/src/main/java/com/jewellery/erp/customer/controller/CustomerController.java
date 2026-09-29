package com.jewellery.erp.customer.controller;

import com.jewellery.erp.common.dto.ApiErrorResponse;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.dto.UpdateStatusRequest;
import com.jewellery.erp.customer.dto.CustomerDto;
import com.jewellery.erp.customer.dto.CustomerRequest;
import com.jewellery.erp.customer.dto.CustomerSummaryDto;
import com.jewellery.erp.customer.service.CustomerService;
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

@RestController
@RequestMapping("/api/customers")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customers", description = "Customers shared by sales and old gold/silver purchases")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.CUSTOMER_VIEW + "')")
    @Operation(summary = "List customers", description = "Search by name, mobile or code. Requires CUSTOMER_VIEW.")
    public ResponseEntity<PageResponse<CustomerDto>> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @ParameterObject @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ResponseEntity.ok(customerService.findAll(search, active, pageable));
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyAuthority('" + PermissionCatalog.CUSTOMER_VIEW + "','"
            + PermissionCatalog.SALES_CREATE + "','" + PermissionCatalog.OLD_METAL_CREATE + "')")
    @Operation(
            summary = "Customer picker search",
            description = "Up to 20 active customers matching name, mobile or code. Open to anyone who can "
                    + "view customers, create a sale, or record an old gold/silver purchase.")
    public ResponseEntity<List<CustomerSummaryDto>> lookup(
            @Parameter(description = "Part of a name, mobile number or customer code") @RequestParam String q) {
        return ResponseEntity.ok(customerService.lookup(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + PermissionCatalog.CUSTOMER_VIEW + "','"
            + PermissionCatalog.SALES_CREATE + "','" + PermissionCatalog.OLD_METAL_CREATE + "')")
    @Operation(summary = "Get one customer")
    @ApiResponse(responseCode = "404", description = "CUSTOMER_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<CustomerDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionCatalog.CUSTOMER_CREATE + "')")
    @Operation(summary = "Create a customer", description = "The customer code is issued by the server. "
            + "Requires CUSTOMER_CREATE.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created"),
        @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CustomerDto> create(@Valid @RequestBody CustomerRequest request) {
        CustomerDto created = customerService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/customers/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.CUSTOMER_EDIT + "')")
    @Operation(summary = "Update a customer", description = "Issued documents keep the details they were "
            + "printed with. Requires CUSTOMER_EDIT.")
    public ResponseEntity<CustomerDto> update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.ok(customerService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.CUSTOMER_EDIT + "')")
    @Operation(summary = "Activate or deactivate a customer", description = "Requires CUSTOMER_EDIT.")
    public ResponseEntity<CustomerDto> updateStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(customerService.updateStatus(id, request.active()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.CUSTOMER_DELETE + "')")
    @Operation(summary = "Delete a customer", description = "Refused once the customer has any sale or purchase "
            + "bill - deactivate instead. Requires CUSTOMER_DELETE.")
    @ApiResponse(responseCode = "409", description = "Customer has transactions",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
