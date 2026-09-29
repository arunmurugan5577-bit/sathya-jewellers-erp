package com.jewellery.erp.report.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.streaming.SXSSFDrawing;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;

/**
 * Writes a report to .xlsx in the shop's colours: a maroon title banner carrying
 * the logo, a gold header row, banded rows, coloured status cells, a maroon
 * totals row, and a Summary sheet up front with the headline figures.
 *
 * <p>Uses POI's streaming workbook, so only a window of rows is kept in memory.
 * Numbers are written as numbers with a display format - never as text - so the
 * accountant can still sum, sort and filter in Excel.
 */
public final class ExcelReportWriter implements AutoCloseable {

    private static final int ROW_WINDOW = 200;
    private static final String LOGO_RESOURCE = "/branding/report-logo.png";
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter GENERATED_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    // Brand palette. MAROON is sampled from the logo's own background so the
    // logo sits seamlessly in the banner.
    private static final XSSFColor MAROON = rgb(0x5A, 0x0A, 0x27);
    private static final XSSFColor MAROON_SOFT = rgb(0x7A, 0x1F, 0x3A);
    private static final XSSFColor GOLD = rgb(0xC9, 0xA2, 0x4D);
    private static final XSSFColor GOLD_LIGHT = rgb(0xF3, 0xDF, 0xA8);
    private static final XSSFColor CREAM = rgb(0xFB, 0xF6, 0xEC);
    private static final XSSFColor BAND = rgb(0xF7, 0xEE, 0xE0);
    private static final XSSFColor WHITE = rgb(0xFF, 0xFF, 0xFF);
    private static final XSSFColor INK = rgb(0x2B, 0x1B, 0x17);
    private static final XSSFColor MUTED = rgb(0x7A, 0x6A, 0x5C);
    private static final XSSFColor RULE = rgb(0xE6, 0xD8, 0xC2);

    /** Status pills: soft fill + strong text. */
    private enum Tone {
        NONE(null, null),
        GREEN(rgb(0xE2, 0xF3, 0xE6), rgb(0x1E, 0x6B, 0x3A)),
        AMBER(rgb(0xFF, 0xF0, 0xD2), rgb(0x8A, 0x5A, 0x00)),
        RED(rgb(0xFB, 0xE2, 0xE2), rgb(0xA1, 0x1B, 0x1B)),
        BLUE(rgb(0xE2, 0xEB, 0xFA), rgb(0x1F, 0x4E, 0x9A));

        final XSSFColor fill;
        final XSSFColor text;

        Tone(XSSFColor fill, XSSFColor text) {
            this.fill = fill;
            this.text = text;
        }
    }

    private static final Map<String, Tone> STATUS_TONES = Map.ofEntries(
            Map.entry("COMPLETED", Tone.GREEN), Map.entry("PAID", Tone.GREEN), Map.entry("AVAILABLE", Tone.GREEN),
            Map.entry("ACTIVE", Tone.GREEN), Map.entry("YES", Tone.GREEN),
            Map.entry("PARTIAL", Tone.AMBER), Map.entry("PARTIALLY_USED", Tone.AMBER),
            Map.entry("UNPAID", Tone.RED), Map.entry("CANCELLED", Tone.RED), Map.entry("NO", Tone.RED),
            Map.entry("REVERSED", Tone.RED),
            Map.entry("SOLD", Tone.BLUE), Map.entry("USED", Tone.BLUE));

    private static final List<String> STATUS_KEYS = List.of("status", "paymentStatus", "lineStatus", "active");

    private final SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_WINDOW);
    private final Map<String, XSSFCellStyle> styles = new HashMap<>();
    private final int logoIndex;
    private final SXSSFSheet summarySheet;
    private final List<SheetWriter> finished = new ArrayList<>();

    public ExcelReportWriter() {
        workbook.setCompressTempFiles(true);
        logoIndex = loadLogo();
        // Created first so it is the tab the file opens on; filled in at the end,
        // once every sheet's totals are known.
        summarySheet = workbook.createSheet("Summary");
    }

    /** A sheet being filled row by row. */
    public final class SheetWriter {
        private final String name;
        private final SXSSFSheet sheet;
        private final List<ReportColumn> columns;
        private final BigDecimal[] totals;
        private final int headerRow;
        private int nextRow;
        private long rowCount;

        private SheetWriter(String name, SXSSFSheet sheet, List<ReportColumn> columns, int headerRow) {
            this.name = name;
            this.sheet = sheet;
            this.columns = columns;
            this.totals = new BigDecimal[columns.size()];
            this.headerRow = headerRow;
            this.nextRow = headerRow + 1;
        }

        public void row(Object[] values) {
            boolean banded = rowCount % 2 == 1;
            Row row = sheet.createRow(nextRow++);
            row.setHeightInPoints(18);
            for (int i = 0; i < columns.size(); i++) {
                ReportColumn column = columns.get(i);
                Object value = values[i];
                Cell cell = row.createCell(i);
                Tone tone = Tone.NONE;
                if (value != null) {
                    switch (column.type()) {
                        case TEXT -> {
                            String text = value.toString();
                            if (STATUS_KEYS.contains(column.key())) {
                                tone = STATUS_TONES.getOrDefault(text.toUpperCase(), Tone.NONE);
                                text = prettyStatus(text);
                            }
                            cell.setCellValue(text);
                        }
                        case DATE -> cell.setCellValue((LocalDate) value);
                        case DATETIME -> cell.setCellValue((LocalDateTime) value);
                        case INTEGER -> cell.setCellValue(((Number) value).doubleValue());
                        // BigDecimal to double only at the Excel boundary: the sheet stores
                        // IEEE doubles, and every value here has at most 3 decimal places.
                        case WEIGHT, MONEY, PERCENT -> cell.setCellValue(((BigDecimal) value).doubleValue());
                    }
                    if (column.total()) {
                        BigDecimal amount = value instanceof BigDecimal d ? d : BigDecimal.valueOf((Long) value);
                        totals[i] = totals[i] == null ? amount : totals[i].add(amount);
                    }
                }
                cell.setCellStyle(dataStyle(column.type(), banded, tone));
            }
            rowCount++;
        }

        public void finish() {
            int last = columns.size() - 1;
            if (rowCount == 0) {
                Row empty = sheet.createRow(nextRow++);
                empty.setHeightInPoints(22);
                for (int i = 0; i <= last; i++) {
                    empty.createCell(i).setCellStyle(style("empty", s -> {
                        fill(s, CREAM);
                        s.setFont(font(false, 10, MUTED, true));
                    }));
                }
                empty.getCell(0).setCellValue("No records for this period.");
                if (last > 0) {
                    sheet.addMergedRegion(new CellRangeAddress(empty.getRowNum(), empty.getRowNum(), 0, last));
                }
            }

            Row row = sheet.createRow(nextRow);
            row.setHeightInPoints(22);
            for (int i = 0; i <= last; i++) {
                ReportColumn column = columns.get(i);
                Cell cell = row.createCell(i);
                cell.setCellStyle(totalStyle(column.type()));
                if (i == 0) {
                    cell.setCellValue("TOTAL  (" + rowCount + (rowCount == 1 ? " row)" : " rows)"));
                } else if (column.total()) {
                    cell.setCellValue(totals[i] == null ? 0 : totals[i].doubleValue());
                }
            }
            if (rowCount > 0) {
                sheet.setAutoFilter(new CellRangeAddress(headerRow, nextRow - 1, 0, last));
            }
            finished.add(this);
        }
    }

    public SheetWriter startSheet(
            String sheetName, List<ReportColumn> columns, String shopName, ReportDefinition definition,
            LocalDateTime generatedAt) {
        SXSSFSheet sheet = workbook.createSheet(sheetName);
        sheet.setDisplayGridlines(false);
        int[] widths = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            ReportColumn column = columns.get(i);
            widths[i] = Math.max(column.width(), column.header().length() + 3);
            sheet.setColumnWidth(i, widths[i] * 256);
        }

        int bannerCols = Math.max(columns.size(), 8);
        int r = banner(sheet, bannerCols, widths, shopName, definition.title() + "  |  " + sheetName,
                definition, generatedAt);

        Row header = sheet.createRow(r);
        header.setHeightInPoints(30);
        for (int i = 0; i < columns.size(); i++) {
            ReportColumn column = columns.get(i);
            Cell cell = header.createCell(i);
            cell.setCellValue(column.header());
            cell.setCellStyle(headerStyle(column.numeric()));
        }
        sheet.createFreezePane(0, r + 1);
        return new SheetWriter(sheetName, sheet, columns, r);
    }

    /** Fills the Summary sheet: one block per sheet with its row count and totals. */
    public void writeSummary(String shopName, ReportDefinition definition, LocalDateTime generatedAt) {
        SXSSFSheet sheet = summarySheet;
        sheet.setDisplayGridlines(false);
        int[] widths = {4, 30, 22, 4, 30, 22, 4, 4};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
        int r = banner(sheet, widths.length, widths, shopName, definition.title() + "  |  Summary", definition,
                generatedAt);

        for (SheetWriter written : finished) {
            Row title = sheet.createRow(r++);
            title.setHeightInPoints(24);
            for (int c = 1; c <= 5; c++) {
                title.createCell(c).setCellStyle(style("sumTitle", s -> {
                    fill(s, MAROON_SOFT);
                    s.setFont(font(true, 12, WHITE, false));
                    s.setVerticalAlignment(VerticalAlignment.CENTER);
                    s.setIndention((short) 1);
                }));
            }
            title.getCell(1).setCellValue(written.name);
            sheet.addMergedRegion(new CellRangeAddress(title.getRowNum(), title.getRowNum(), 1, 5));

            List<Object[]> figures = new ArrayList<>();
            figures.add(new Object[] {"Records", written.rowCount, ReportColumn.Type.INTEGER});
            for (int i = 0; i < written.columns.size(); i++) {
                ReportColumn column = written.columns.get(i);
                if (column.total()) {
                    figures.add(new Object[] {
                        column.header(), written.totals[i] == null ? BigDecimal.ZERO : written.totals[i], column.type()
                    });
                }
            }

            // Two label/value pairs per row, like cards.
            for (int f = 0; f < figures.size(); f += 2) {
                Row row = sheet.createRow(r++);
                row.setHeightInPoints(26);
                for (int c = 1; c <= 5; c++) {
                    row.createCell(c).setCellStyle(style("sumGap", s -> fill(s, CREAM)));
                }
                writeFigure(row, 1, figures.get(f));
                if (f + 1 < figures.size()) {
                    writeFigure(row, 4, figures.get(f + 1));
                }
            }
            r++;
        }

        Row note = sheet.createRow(r + 1);
        note.createCell(1).setCellValue("Detailed rows are on the following sheets. Figures are in Indian Rupees; "
                + "weights in grams.");
        note.getCell(1).setCellStyle(style("note", s -> s.setFont(font(false, 9, MUTED, true))));
        workbook.setActiveSheet(0);
    }

    public void write(OutputStream out) throws IOException {
        workbook.write(out);
    }

    @Override
    public void close() throws IOException {
        workbook.close(); // also deletes POI's temporary files
    }

    // ------------------------------------------------------------------ banner

    /**
     * Maroon banner with the logo on the left, shop name, report title and period
     * beside it, then a cream strip carrying the filters.
     *
     * @return the row index where the table header should go
     */
    private int banner(
            SXSSFSheet sheet, int cols, int[] widths, String shopName, String title, ReportDefinition definition,
            LocalDateTime generatedAt) {
        int textCol = columnAfterLogo(widths);
        float[] heights = {30, 22, 18, 8};
        for (int r = 0; r < heights.length; r++) {
            Row row = sheet.createRow(r);
            row.setHeightInPoints(heights[r]);
            for (int c = 0; c < cols; c++) {
                row.createCell(c).setCellStyle(style("band", s -> fill(s, MAROON)));
            }
        }
        text(sheet.getRow(0), textCol, shopName.toUpperCase(), style("bannerShop", s -> {
            fill(s, MAROON);
            s.setFont(font(true, 18, WHITE, false));
            s.setVerticalAlignment(VerticalAlignment.BOTTOM);
        }));
        text(sheet.getRow(1), textCol, title, style("bannerTitle", s -> {
            fill(s, MAROON);
            s.setFont(font(true, 13, GOLD, false));
            s.setVerticalAlignment(VerticalAlignment.CENTER);
        }));
        text(sheet.getRow(2), textCol, "Period: %s  to  %s        Generated: %s".formatted(
                PERIOD_FORMAT.format(definition.startDate()), PERIOD_FORMAT.format(definition.endDate()),
                GENERATED_FORMAT.format(generatedAt)), style("bannerMeta", s -> {
            fill(s, MAROON);
            s.setFont(font(false, 10, GOLD_LIGHT, false));
            s.setVerticalAlignment(VerticalAlignment.TOP);
        }));

        // Gold rule under the banner.
        Row rule = sheet.createRow(4);
        rule.setHeightInPoints(4);
        for (int c = 0; c < cols; c++) {
            rule.createCell(c).setCellStyle(style("goldRule", s -> fill(s, GOLD)));
        }

        Row filters = sheet.createRow(5);
        filters.setHeightInPoints(22);
        for (int c = 0; c < cols; c++) {
            filters.createCell(c).setCellStyle(style("filters", s -> {
                fill(s, CREAM);
                s.setFont(font(false, 10, MAROON, true));
                s.setVerticalAlignment(VerticalAlignment.CENTER);
                s.setIndention((short) 1);
            }));
        }
        filters.getCell(0).setCellValue(definition.filterDescriptions().isEmpty()
                ? "Filters: none - all records in the period"
                : "Filters: " + String.join("   •   ", definition.filterDescriptions()));

        placeLogo(sheet);
        return 7;
    }

    private void placeLogo(SXSSFSheet sheet) {
        if (logoIndex < 0) {
            return;
        }
        SXSSFDrawing drawing = sheet.createDrawingPatriarch();
        ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();
        anchor.setAnchorType(ClientAnchor.AnchorType.DONT_MOVE_AND_RESIZE);
        anchor.setCol1(0);
        anchor.setRow1(0);
        // The banner is 78pt (104px) tall; scale the 774x258 logo to about 279x93px so it
        // fits with a small margin. resize() keeps the 3:1 shape.
        anchor.setDx1(6 * 9525);
        anchor.setDy1(4 * 9525);
        drawing.createPicture(anchor, logoIndex).resize(0.36);
    }

    /** The first column whose left edge clears the logo (about 280 px wide) with room to spare. */
    private static int columnAfterLogo(int[] widths) {
        int pixels = 0;
        for (int i = 0; i < widths.length; i++) {
            if (pixels >= 330) {
                return i;
            }
            pixels += widths[i] * 7 + 5;
        }
        return Math.min(2, widths.length - 1);
    }

    private int loadLogo() {
        try (InputStream in = ExcelReportWriter.class.getResourceAsStream(LOGO_RESOURCE)) {
            if (in == null) {
                return -1;
            }
            return workbook.addPicture(IOUtils.toByteArray(in), SXSSFWorkbook.PICTURE_TYPE_PNG);
        } catch (IOException ex) {
            return -1; // a report without a logo beats no report
        }
    }

    // ------------------------------------------------------------------ styles

    private void writeFigure(Row row, int col, Object[] figure) {
        Cell label = row.getCell(col);
        label.setCellValue(figure[0].toString());
        label.setCellStyle(style("figLabel", s -> {
            fill(s, CREAM);
            s.setFont(font(false, 10, MUTED, false));
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            s.setIndention((short) 1);
            s.setBorderLeft(BorderStyle.THICK);
            s.setLeftBorderColor(GOLD);
        }));
        Cell value = row.getCell(col + 1);
        Object number = figure[1];
        value.setCellValue(number instanceof BigDecimal d ? d.doubleValue() : ((Number) number).doubleValue());
        ReportColumn.Type type = (ReportColumn.Type) figure[2];
        value.setCellStyle(style("figValue" + type, s -> {
            fill(s, CREAM);
            s.setFont(font(true, 13, MAROON, false));
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            s.setAlignment(HorizontalAlignment.RIGHT);
            s.setDataFormat(format(type));
        }));
    }

    private XSSFCellStyle headerStyle(boolean numeric) {
        return style("header" + numeric, s -> {
            fill(s, GOLD);
            s.setFont(font(true, 10, MAROON, false));
            s.setAlignment(numeric ? HorizontalAlignment.RIGHT : HorizontalAlignment.LEFT);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            s.setWrapText(true);
            s.setBorderBottom(BorderStyle.MEDIUM);
            s.setBottomBorderColor(MAROON);
            s.setBorderRight(BorderStyle.THIN);
            s.setRightBorderColor(GOLD_LIGHT);
        });
    }

    private XSSFCellStyle dataStyle(ReportColumn.Type type, boolean banded, Tone tone) {
        return style("data" + type + banded + tone, s -> {
            fill(s, tone.fill != null ? tone.fill : banded ? BAND : WHITE);
            s.setFont(font(tone != Tone.NONE, 10, tone.text != null ? tone.text : INK, false));
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            s.setBorderBottom(BorderStyle.THIN);
            s.setBottomBorderColor(RULE);
            if (tone != Tone.NONE) {
                s.setAlignment(HorizontalAlignment.CENTER);
            } else if (type != ReportColumn.Type.TEXT) {
                s.setAlignment(HorizontalAlignment.RIGHT);
            }
            s.setDataFormat(format(type));
        });
    }

    private XSSFCellStyle totalStyle(ReportColumn.Type type) {
        return style("total" + type, s -> {
            fill(s, MAROON);
            s.setFont(font(true, 11, WHITE, false));
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            s.setBorderTop(BorderStyle.MEDIUM);
            s.setTopBorderColor(GOLD);
            if (type != ReportColumn.Type.TEXT) {
                s.setAlignment(HorizontalAlignment.RIGHT);
            }
            s.setDataFormat(format(type));
        });
    }

    private short format(ReportColumn.Type type) {
        String pattern = switch (type) {
            case DATE -> "dd-mmm-yyyy";
            case DATETIME -> "dd-mmm-yyyy hh:mm AM/PM";
            case INTEGER -> "#,##0";
            case WEIGHT -> "#,##0.000\" g\"";
            case MONEY -> "\"₹\" #,##0.00;[Red]-\"₹\" #,##0.00";
            case PERCENT -> "0.00\"%\"";
            case TEXT -> "@";
        };
        return workbook.createDataFormat().getFormat(pattern);
    }

    private XSSFCellStyle style(String key, java.util.function.Consumer<XSSFCellStyle> init) {
        return styles.computeIfAbsent(key, k -> {
            XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
            init.accept(style);
            return style;
        });
    }

    private XSSFFont font(boolean bold, int points, XSSFColor color, boolean italic) {
        XSSFFont font = (XSSFFont) workbook.createFont();
        font.setFontName("Calibri");
        font.setBold(bold);
        font.setItalic(italic);
        font.setFontHeightInPoints((short) points);
        font.setColor(color);
        return font;
    }

    private static void fill(XSSFCellStyle style, XSSFColor color) {
        style.setFillForegroundColor(color);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }

    private static void text(Row row, int col, String value, XSSFCellStyle style) {
        Cell cell = row.getCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static String prettyStatus(String value) {
        return switch (value.toUpperCase()) {
            case "PARTIAL" -> "Part paid";
            case "PARTIALLY_USED" -> "Partly used";
            case "AVAILABLE" -> "In stock";
            default -> value.charAt(0) + value.substring(1).toLowerCase().replace('_', ' ');
        };
    }

    private static XSSFColor rgb(int r, int g, int b) {
        return new XSSFColor(new byte[] {(byte) r, (byte) g, (byte) b});
    }
}
