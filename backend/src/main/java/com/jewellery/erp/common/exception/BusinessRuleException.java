package com.jewellery.erp.common.exception;

import java.util.Map;

/**
 * Thrown when input is syntactically valid but violates a domain rule - for
 * example a purity that does not belong to the selected item type. Mapped to
 * HTTP 400.
 */
public class BusinessRuleException extends RuntimeException {

    private final Map<String, String> fieldErrors;
    private final ErrorCode code;

    public BusinessRuleException(String message) {
        this(message, Map.of());
    }

    public BusinessRuleException(String field, String message) {
        this(message, Map.of(field, message));
    }

    public BusinessRuleException(String message, Map<String, String> fieldErrors) {
        this(null, message, fieldErrors);
    }

    public BusinessRuleException(ErrorCode code, String field, String message) {
        this(code, message, field == null ? Map.of() : Map.of(field, message));
    }

    public BusinessRuleException(ErrorCode code, String message, Map<String, String> fieldErrors) {
        super(message);
        this.code = code;
        this.fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    /** Null for rules raised before error codes existed; the handler falls back to VALIDATION_FAILED. */
    public ErrorCode getCode() {
        return code;
    }
}
