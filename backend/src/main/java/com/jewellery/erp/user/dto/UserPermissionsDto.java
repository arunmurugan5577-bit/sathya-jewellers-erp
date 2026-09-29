package com.jewellery.erp.user.dto;

import com.jewellery.erp.permission.dto.ModulePermissionsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;

/**
 * Everything the permission screen needs in one call.
 *
 * @param modules the full catalogue, so the matrix can render every checkbox
 * @param directPermissionIds the boxes that are ticked and editable
 * @param rolePermissionCodes permissions the user already holds through a role;
 *     the UI shows these ticked and disabled, because unticking them would have
 *     no effect
 */
@Schema(name = "UserPermissions")
public record UserPermissionsDto(
        Long userId,
        String username,
        String fullName,
        List<String> roles,
        boolean administrator,
        List<ModulePermissionsDto> modules,
        Set<Long> directPermissionIds,
        Set<String> rolePermissionCodes) {}
