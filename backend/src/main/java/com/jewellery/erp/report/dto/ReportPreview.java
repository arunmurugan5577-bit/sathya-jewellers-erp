package com.jewellery.erp.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * What the report page shows before download: the first rows of each sheet and
 * totals over ALL matching rows, computed by the same query the Excel file uses.
 */
@Schema(name = "ReportPreview")
public record ReportPreview(
        String title,
        LocalDate startDate,
        LocalDate endDate,
        List<String> filters,
        List<SheetPreview> sheets) {

    @Schema(name = "ReportSheetPreview")
    public record SheetPreview(
            String name,
            List<Column> columns,
            List<Map<String, Object>> rows,
            long rowCount,
            boolean truncated,
            Map<String, BigDecimal> totals) {}

    @Schema(name = "ReportColumn")
    public record Column(String key, String header, @Schema(example = "MONEY") String type, boolean total) {}
}
