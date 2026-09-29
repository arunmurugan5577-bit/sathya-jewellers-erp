package com.jewellery.erp.report.service;

import com.jewellery.erp.common.config.BusinessClock;
import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.ErrorCode;
import com.jewellery.erp.report.dto.ReportPreview;
import com.jewellery.erp.report.engine.ExcelReportWriter;
import com.jewellery.erp.report.engine.ReportColumn;
import com.jewellery.erp.report.engine.ReportDefinition;
import com.jewellery.erp.report.engine.ReportRunner;
import com.jewellery.erp.shop.service.ShopSettingsService;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds report previews and Excel files.
 *
 * <p>An export is written to a temporary file inside a read-only transaction and
 * only then streamed to the browser. That keeps the database connection free while
 * the file travels over a slow shop connection, and it means a failure half way
 * through produces a proper JSON error rather than a truncated download.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final int PREVIEW_ROWS = 100;

    private final ReportDefinitions definitions;
    private final ReportRunner runner;
    private final ShopSettingsService shopSettingsService;
    private final BusinessClock businessClock;
    private final JdbcTemplate jdbc;
    private final int maxRangeDays;

    public ReportService(
            ReportDefinitions definitions,
            ReportRunner runner,
            ShopSettingsService shopSettingsService,
            BusinessClock businessClock,
            DataSource dataSource,
            @Value("${app.reports.max-range-days:366}") int maxRangeDays) {
        this.definitions = definitions;
        this.runner = runner;
        this.shopSettingsService = shopSettingsService;
        this.businessClock = businessClock;
        this.jdbc = new JdbcTemplate(dataSource);
        this.maxRangeDays = maxRangeDays;
    }

    /** A generated file, waiting in the temp directory to be streamed and deleted. */
    public record ReportFile(Path path, String fileName, long size) {}

    public ReportPreview previewStock(StockReportFilter filter) {
        return preview(stockDefinition(filter));
    }

    public ReportPreview previewSales(SalesReportFilter filter) {
        return preview(salesDefinition(filter));
    }

    public ReportFile exportStock(StockReportFilter filter) {
        return export(stockDefinition(filter));
    }

    public ReportFile exportSales(SalesReportFilter filter) {
        return export(salesDefinition(filter));
    }

    // ------------------------------------------------------------- helpers ---

    private ReportDefinition stockDefinition(StockReportFilter filter) {
        validateRange(filter.startDate(), filter.endDate());
        Map<String, String> labels = new HashMap<>();
        if (filter.itemTypeId() != null) {
            labels.put("itemType", name("item_types", "name", filter.itemTypeId()));
        }
        if (filter.categoryId() != null) {
            labels.put("category", name("categories", "name", filter.categoryId()));
        }
        return definitions.stock(filter, labels);
    }

    private ReportDefinition salesDefinition(SalesReportFilter filter) {
        validateRange(filter.startDate(), filter.endDate());
        Map<String, String> labels = new HashMap<>();
        if (filter.customerId() != null) {
            labels.put("customer", name("customers", "full_name", filter.customerId()));
        }
        return definitions.sales(filter, labels);
    }

    private ReportPreview preview(ReportDefinition definition) {
        List<ReportPreview.SheetPreview> sheets = new ArrayList<>();
        for (ReportDefinition.Sheet sheet : definition.sheets()) {
            List<ReportColumn> columns = sheet.columns();
            List<Map<String, Object>> rows = new ArrayList<>();
            Map<String, BigDecimal> totals = new LinkedHashMap<>();
            columns.stream().filter(ReportColumn::total).forEach(c -> totals.put(c.key(), BigDecimal.ZERO));
            long[] count = {0};

            runner.run(sheet, values -> {
                count[0]++;
                if (rows.size() < PREVIEW_ROWS) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 0; i < columns.size(); i++) {
                        row.put(columns.get(i).key(), values[i]);
                    }
                    rows.add(row);
                }
                for (int i = 0; i < columns.size(); i++) {
                    if (columns.get(i).total() && values[i] != null) {
                        BigDecimal value = values[i] instanceof BigDecimal d ? d : BigDecimal.valueOf((Long) values[i]);
                        totals.merge(columns.get(i).key(), value, BigDecimal::add);
                    }
                }
            });

            sheets.add(new ReportPreview.SheetPreview(
                    sheet.name(),
                    columns.stream()
                            .map(c -> new ReportPreview.Column(c.key(), c.header(), c.type().name(), c.total()))
                            .toList(),
                    rows,
                    count[0],
                    count[0] > rows.size(),
                    totals));
        }
        return new ReportPreview(definition.title(), definition.startDate(), definition.endDate(),
                definition.filterDescriptions(), sheets);
    }

    private ReportFile export(ReportDefinition definition) {
        String shopName = shopSettingsService.shopName();
        LocalDateTime generatedAt = LocalDateTime.now(businessClock.zone());
        String fileName = "%s_%s_to_%s.xlsx".formatted(
                definition.fileNamePrefix(), definition.startDate(), definition.endDate());

        Path file = null;
        try (ExcelReportWriter writer = new ExcelReportWriter()) {
            long rows = 0;
            for (ReportDefinition.Sheet sheet : definition.sheets()) {
                ExcelReportWriter.SheetWriter sheetWriter =
                        writer.startSheet(sheet.name(), sheet.columns(), shopName, definition, generatedAt);
                long[] count = {0};
                runner.run(sheet, values -> {
                    sheetWriter.row(values);
                    count[0]++;
                });
                sheetWriter.finish();
                rows += count[0];
            }
            writer.writeSummary(shopName, definition, generatedAt);
            file = Files.createTempFile("jewellery-report-", ".xlsx");
            try (OutputStream out = Files.newOutputStream(file)) {
                writer.write(out);
            }
            long size = Files.size(file);
            log.info("{} generated for {} to {}: {} row(s), {} bytes",
                    definition.title(), definition.startDate(), definition.endDate(), rows, size);
            return new ReportFile(file, fileName, size);
        } catch (IOException ex) {
            deleteQuietly(file);
            throw new UncheckedIOException("Could not generate " + definition.title(), ex);
        } catch (RuntimeException ex) {
            deleteQuietly(file);
            throw ex;
        }
    }

    private void validateRange(LocalDate start, LocalDate end) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (start == null) {
            errors.put("startDate", "Start date is required.");
        }
        if (end == null) {
            errors.put("endDate", "End date is required.");
        }
        if (!errors.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.INVALID_DATE_RANGE, "Start date and end date are required.",
                    errors);
        }
        if (start.isAfter(end)) {
            throw new BusinessRuleException(ErrorCode.INVALID_DATE_RANGE, "endDate",
                    "End date cannot be before the start date.");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > maxRangeDays) {
            throw new BusinessRuleException(ErrorCode.INVALID_DATE_RANGE, "endDate",
                    "A report can cover at most %d days; this range is %d.".formatted(maxRangeDays, days));
        }
    }

    /** A display name for a filter; table and column names are constants, never user input. */
    private String name(String table, String column, Long id) {
        List<String> names = jdbc.queryForList(
                "SELECT " + column + " FROM " + table + " WHERE id = ?", String.class, id);
        return names.isEmpty() ? "#" + id : names.get(0);
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            log.warn("Could not delete temporary report file {}", file, ex);
        }
    }
}
