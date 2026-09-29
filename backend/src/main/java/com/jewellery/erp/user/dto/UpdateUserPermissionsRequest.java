package com.jewellery.erp.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * Replaces a user's direct permission grants wholesale.
 *
 * <p>A full replacement rather than add/remove deltas: the permission screen
 * shows the complete matrix, so sending the complete result avoids the lost
 * update that two administrators editing at once would otherwise produce.
 */
@Schema(name = "UpdateUserPermissionsRequest")
public record UpdateUserPermissionsRequest(
        @NotNull(message = "Permission list is required")
        @Schema(description = "The complete set of permission ids the user should hold directly")
        Set<Long> permissionIds) {}
