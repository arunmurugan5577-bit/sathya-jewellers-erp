package com.jewellery.erp.common.exception;

/**
 * Thrown when a uniqueness rule is violated (duplicate code, name, serial
 * number, ...). Mapped to HTTP 409 with a {@code fieldErrors} entry so the
 * frontend can highlight the offending control.
 */
public class DuplicateResourceException extends RuntimeException {

    private final String field;

    public DuplicateResourceException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
