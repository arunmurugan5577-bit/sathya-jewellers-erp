package com.jewellery.erp.dashboard.controller;

import com.jewellery.erp.dashboard.dto.DashboardSummaryDto;
import com.jewellery.erp.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Landing page metrics.
 *
 * <p>Open to any signed-in user, because the response only ever contains the
 * metrics that user's permissions allow.
 */
@RestController
@RequestMapping("/api/dashboard")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Dashboard", description = "Summary counters for the landing page")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Dashboard summary",
            description = "Returns only the metrics the signed-in user is permitted to see; "
                    + "everything else is omitted from the payload.")
    public ResponseEntity<DashboardSummaryDto> summary() {
        return ResponseEntity.ok(dashboardService.summarise());
    }
}
