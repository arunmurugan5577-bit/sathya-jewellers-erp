package com.jewellery.erp.common.exception;

import java.util.Map;

/**
 * The request was valid, but the current state of the data forbids it: the item
 * has already been sold, the old gold has already been used. Mapped to HTTP 409.
 *
 * <p>Distinct from {@link BusinessRuleException} (400) because retrying a 409 can
 * succeed once the state changes, whereas a 400 will fail identically forever.
 */
public class StateConflictException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, String> fieldErrors;

    public StateConflictException(ErrorCode code, String message) {
        this(code, null, message);
    }

    public StateConflictException(ErrorCode code, String field, String message) {
        super(message);
        this.code = code;
        this.fieldErrors = field == null ? Map.of() : Map.of(field, message);
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
