package com.jewellery.erp.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Activate or deactivate a record.
 *
 * <p>Shared by users and every master module: soft deletion is the default
 * across the system, so the payload for it is defined once.
 */
@Schema(name = "UpdateStatusRequest")
public record UpdateStatusRequest(@NotNull(message = "Status is required") Boolean active) {}
