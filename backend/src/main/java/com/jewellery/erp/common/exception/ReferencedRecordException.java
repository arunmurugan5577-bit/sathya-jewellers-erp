package com.jewellery.erp.common.exception;

/**
 * Thrown when a record cannot be hard deleted because other records still
 * reference it. The caller is expected to deactivate it instead. Mapped to
 * HTTP 409.
 */
public class ReferencedRecordException extends RuntimeException {

    public ReferencedRecordException(String message) {
        super(message);
    }

    public static ReferencedRecordException of(String resource, String name, String referencedBy) {
        return new ReferencedRecordException(
                "%s '%s' is referenced by existing %s and cannot be deleted. Deactivate it instead."
                        .formatted(resource, name, referencedBy));
    }
}
