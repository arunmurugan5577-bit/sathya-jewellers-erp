package com.jewellery.erp.common.exception;

/** Thrown when a requested record does not exist. Mapped to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    private final ErrorCode code;

    public ResourceNotFoundException(String message) {
        this(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public ResourceNotFoundException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /** Convenience factory producing a consistent "Category with id 7 was not found." message. */
    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException("%s with id %s was not found.".formatted(resource, id));
    }

    public static ResourceNotFoundException of(ErrorCode code, String resource, Object id) {
        return new ResourceNotFoundException(code, "%s with id %s was not found.".formatted(resource, id));
    }

    public ErrorCode getCode() {
        return code;
    }
}
