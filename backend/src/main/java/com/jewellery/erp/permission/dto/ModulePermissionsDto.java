package com.jewellery.erp.permission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * One row of the permission matrix shown on the user permission screen: a module
 * and the actions that can be granted for it.
 */
@Schema(name = "ModulePermissions")
public record ModulePermissionsDto(
        @Schema(example = "CATEGORY") String module,
        @Schema(example = "Categories") String label,
        List<PermissionDto> permissions) {}
