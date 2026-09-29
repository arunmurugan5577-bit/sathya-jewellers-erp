package com.jewellery.erp.report.engine;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** A report ready to run: its title, period, the filters applied, and one or more sheets. */
public record ReportDefinition(
        String title,
        String fileNamePrefix,
        LocalDate startDate,
        LocalDate endDate,
        List<String> filterDescriptions,
        List<Sheet> sheets) {

    /** One worksheet: a parameterised query whose select list matches {@code columns}. */
    public record Sheet(String name, List<ReportColumn> columns, String sql, Map<String, Object> parameters) {}
}
