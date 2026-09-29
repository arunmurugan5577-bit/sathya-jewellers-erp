package com.jewellery.erp.numbering;

import java.time.LocalDate;
import java.time.Month;

/**
 * Pure formatting rules for document numbers, kept free of the database so they
 * can be tested directly.
 *
 * <pre>
 *   [prefix][sep][period][sep][zero-padded sequence]
 *   INV-2026-000001
 * </pre>
 */
public final class DocumentNumberFormat {

    /** Key used for a series that never resets. */
    public static final String NO_PERIOD = "ALL";

    private DocumentNumberFormat() {}

    /**
     * The counter bucket a document dated {@code date} draws from.
     *
     * <p>The Indian financial year runs 1 April to 31 March and is labelled by the
     * year it starts in, so a sale on 15 March 2027 belongs to "2026".
     */
    public static String periodKey(String periodFormat, LocalDate date) {
        return switch (periodFormat) {
            case "NONE" -> NO_PERIOD;
            case "CALENDAR_YEAR" -> String.valueOf(date.getYear());
            case "FINANCIAL_YEAR" -> String.valueOf(
                    date.getMonthValue() >= Month.APRIL.getValue() ? date.getYear() : date.getYear() - 1);
            default -> throw new IllegalStateException("Unknown period format: " + periodFormat);
        };
    }

    public static String format(String prefix, String separator, String periodKey, int padWidth, long value) {
        StringBuilder number = new StringBuilder();
        if (prefix != null && !prefix.isEmpty()) {
            number.append(prefix).append(separator);
        }
        if (!NO_PERIOD.equals(periodKey)) {
            number.append(periodKey).append(separator);
        }
        return number.append(String.format("%0" + padWidth + "d", value)).toString();
    }
}
