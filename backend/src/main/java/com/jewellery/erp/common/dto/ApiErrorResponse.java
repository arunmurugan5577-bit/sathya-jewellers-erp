package com.jewellery.erp.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/**
 * The single error envelope returned by every failing endpoint.
 *
 * <p>Successful responses deliberately return their DTO directly - only errors
 * are wrapped, which keeps pagination payloads clean while still giving the
 * frontend one predictable shape to parse failures from.
 *
 * <p>{@code code} is the stable, machine-readable reason (see {@code ErrorCode});
 * {@code message} is for people and may be reworded. It is additive: older error
 * responses without a code remain valid.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiError", description = "Standard error payload")
public record ApiErrorResponse(
        @Schema(example = "2026-09-15T09:31:22.417Z") Instant timestamp,
        @Schema(example = "409") int status,
        @Schema(example = "Conflict") String error,
        @Schema(example = "ITEM_ALREADY_SOLD", description = "Stable machine-readable failure code")
        String code,
        @Schema(example = "Serial number 123456 has already been sold.") String message,
        @Schema(example = "/api/sales") String path,
        @Schema(description = "Field level messages keyed by field name")
        Map<String, String> fieldErrors) {

    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return of(status, error, null, message, path, null);
    }

    public static ApiErrorResponse of(
            int status, String error, String message, String path, Map<String, String> fieldErrors) {
        return of(status, error, null, message, path, fieldErrors);
    }

    public static ApiErrorResponse of(
            int status,
            String error,
            String code,
            String message,
            String path,
            Map<String, String> fieldErrors) {
        return new ApiErrorResponse(
                Instant.now(),
                status,
                error,
                code,
                message,
                path,
                fieldErrors == null || fieldErrors.isEmpty() ? null : fieldErrors);
    }
}
