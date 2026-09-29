package com.jewellery.erp.permission.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Permission")
public record PermissionDto(
        Long id,
        @Schema(example = "CATEGORY_CREATE") String code,
        @Schema(example = "CATEGORY") String module,
        @Schema(example = "CREATE") String action,
        String description) {}
