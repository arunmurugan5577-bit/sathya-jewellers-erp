package com.jewellery.erp.common.util;

/**
 * Input normalisation applied consistently before persisting master data.
 *
 * <p>Trailing whitespace is the single most common cause of "duplicate" master
 * records that look identical to the user, so every service normalises through
 * this class rather than trusting the payload.
 */
public final class StringNormalizer {

    private StringNormalizer() {}

    /** Trims, collapsing an empty or whitespace-only value to {@code null}. */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Trims and collapses runs of internal whitespace to a single space. */
    public static String normalizeName(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.replaceAll("\s+", " ");
    }

    /** Master codes are stored upper case so that lookups are predictable. */
    public static String normalizeCode(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }

    /** Usernames and e-mail addresses are compared case-insensitively. */
    public static String normalizeLower(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase();
    }
}
