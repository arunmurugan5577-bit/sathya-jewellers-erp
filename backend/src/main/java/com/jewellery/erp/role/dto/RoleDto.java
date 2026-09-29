package com.jewellery.erp.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Role")
public record RoleDto(
        Long id,
        @Schema(example = "ROLE_ADMIN") String name,
        @Schema(example = "Administrator") String label,
        String description,
        @Schema(description = "Number of permissions the role carries") int permissionCount) {}
