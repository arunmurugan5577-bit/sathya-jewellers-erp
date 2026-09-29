package com.jewellery.erp.report.engine;

/**
 * One column of a report: how to read it from the SQL row, how to format it in
 * Excel, and whether it is summed in the totals row. The SQL select list must
 * return columns in the same order as the definition.
 */
public record ReportColumn(String key, String header, Type type, boolean total, int width) {

    public enum Type {
        TEXT,
        DATE,
        DATETIME,
        INTEGER,
        WEIGHT,
        MONEY,
        PERCENT
    }

    public static ReportColumn text(String key, String header, int width) {
        return new ReportColumn(key, header, Type.TEXT, false, width);
    }

    public static ReportColumn date(String key, String header) {
        return new ReportColumn(key, header, Type.DATE, false, 12);
    }

    public static ReportColumn dateTime(String key, String header) {
        return new ReportColumn(key, header, Type.DATETIME, false, 18);
    }

    public static ReportColumn integer(String key, String header, boolean total) {
        return new ReportColumn(key, header, Type.INTEGER, total, 8);
    }

    public static ReportColumn weight(String key, String header, boolean total) {
        return new ReportColumn(key, header, Type.WEIGHT, total, 12);
    }

    public static ReportColumn money(String key, String header, boolean total) {
        return new ReportColumn(key, header, Type.MONEY, total, 14);
    }

    public static ReportColumn percent(String key, String header) {
        return new ReportColumn(key, header, Type.PERCENT, false, 9);
    }

    public boolean numeric() {
        return type == Type.INTEGER || type == Type.WEIGHT || type == Type.MONEY || type == Type.PERCENT;
    }
}
