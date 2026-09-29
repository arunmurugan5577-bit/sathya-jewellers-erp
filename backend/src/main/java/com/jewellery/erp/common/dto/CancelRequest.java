package com.jewellery.erp.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cancels a financial document.
 *
 * <p>The reason is mandatory: a cancelled invoice stays in the database for ever,
 * and "why was this cancelled" is the first question anyone reviewing it will ask.
 */
@Schema(name = "CancelRequest")
public record CancelRequest(
        @Schema(example = "Wrong item billed; re-issued as a new invoice")
        @NotBlank(message = "A cancellation reason is required")
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String reason) {}
