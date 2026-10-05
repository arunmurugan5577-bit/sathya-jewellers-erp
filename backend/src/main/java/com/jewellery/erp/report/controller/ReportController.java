package com.jewellery.erp.report.controller;

import com.jewellery.erp.inventory.entity.InventoryStatus;
import com.jewellery.erp.permission.PermissionCatalog;
import com.jewellery.erp.report.dto.ReportPreview;
import com.jewellery.erp.wholesale.dto.WholesaleReportFilter;
import com.jewellery.erp.wholesale.entity.WholesaleStatus;
import com.jewellery.erp.report.service.ReportService;
import com.jewellery.erp.report.service.SalesReportFilter;
import com.jewellery.erp.report.service.StockReportFilter;
import com.jewellery.erp.sales.entity.PaymentStatus;
import com.jewellery.erp.sales.entity.SaleStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Reports", description = "Stock and sales reports, previewed on screen and exported to Excel")
public class ReportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/stock/preview")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.REPORT_STOCK_VIEW + "')")
    @Operation(summary = "Preview the stock report",
            description = "Pieces stocked in between the dates. Requires REPORT_STOCK_VIEW.")
    public ResponseEntity<ReportPreview> previewStock(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long itemTypeId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) InventoryStatus status,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(reportService.previewStock(
                new StockReportFilter(startDate, endDate, itemTypeId, categoryId, status, includeInactive)));
    }

    @GetMapping("/stock/export")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.REPORT_STOCK_EXPORT + "')")
    @Operation(summary = "Download the stock report as Excel", description = "Requires REPORT_STOCK_EXPORT.")
    public ResponseEntity<InputStreamResource> exportStock(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long itemTypeId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) InventoryStatus status,
            @RequestParam(defaultValue = "false") boolean includeInactive) throws IOException {
        return download(reportService.exportStock(
                new StockReportFilter(startDate, endDate, itemTypeId, categoryId, status, includeInactive)));
    }

    @GetMapping("/sales/preview")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.REPORT_SALES_VIEW + "')")
    @Operation(summary = "Preview the sales report",
            description = "Invoices dated between the dates; completed only unless status=ALL or CANCELLED. "
                    + "Requires REPORT_SALES_VIEW.")
    public ResponseEntity<ReportPreview> previewSales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "COMPLETED (default), CANCELLED or ALL")
            @RequestParam(defaultValue = "COMPLETED") SaleStatusFilter status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) Long customerId) {
        return ResponseEntity.ok(reportService.previewSales(
                new SalesReportFilter(startDate, endDate, status.toStatus(), paymentStatus, customerId)));
    }

    @GetMapping("/sales/export")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.REPORT_SALES_EXPORT + "')")
    @Operation(summary = "Download the sales report as Excel",
            description = "Three sheets: Invoices, Items, Payments. Requires REPORT_SALES_EXPORT.")
    public ResponseEntity<InputStreamResource> exportSales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "COMPLETED") SaleStatusFilter status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) Long customerId) throws IOException {
        return download(reportService.exportSales(
                new SalesReportFilter(startDate, endDate, status.toStatus(), paymentStatus, customerId)));
    }

    @GetMapping("/wholesale/preview")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.REPORT_WHOLESALE_VIEW + "')")
    @Operation(summary = "Preview the wholesale report",
            description = "Estimates dated between the dates, their pieces, and every party account that "
                    + "is not square. Requires REPORT_WHOLESALE_VIEW.")
    public ResponseEntity<ReportPreview> previewWholesale(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "COMPLETED (default), CANCELLED or ALL")
            @RequestParam(defaultValue = "COMPLETED") WholesaleStatusFilter status,
            @RequestParam(required = false) Long customerId) {
        return ResponseEntity.ok(reportService.previewWholesale(
                new WholesaleReportFilter(startDate, endDate, status.toStatus(), customerId)));
    }

    @GetMapping("/wholesale/export")
    @PreAuthorize("hasAuthority('" + PermissionCatalog.REPORT_WHOLESALE_EXPORT + "')")
    @Operation(summary = "Download the wholesale report as Excel",
            description = "Three sheets: Estimates, Items, Party Balances. Requires REPORT_WHOLESALE_EXPORT.")
    public ResponseEntity<InputStreamResource> exportWholesale(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "COMPLETED") WholesaleStatusFilter status,
            @RequestParam(required = false) Long customerId) throws IOException {
        return download(reportService.exportWholesale(
                new WholesaleReportFilter(startDate, endDate, status.toStatus(), customerId)));
    }

    public enum WholesaleStatusFilter {
        COMPLETED,
        CANCELLED,
        ALL;

        WholesaleStatus toStatus() {
            return this == ALL ? null : WholesaleStatus.valueOf(name());
        }
    }

    public enum SaleStatusFilter {
        COMPLETED,
        CANCELLED,
        ALL;

        SaleStatus toStatus() {
            return this == ALL ? null : SaleStatus.valueOf(name());
        }
    }

    private static ResponseEntity<InputStreamResource> download(ReportService.ReportFile file) throws IOException {
        // DELETE_ON_CLOSE: the temp file disappears once the response has been sent.
        InputStreamResource body = new InputStreamResource(
                Files.newInputStream(file.path(), StandardOpenOption.DELETE_ON_CLOSE));
        return ResponseEntity.ok()
                .contentType(XLSX)
                .contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(body);
    }
}
