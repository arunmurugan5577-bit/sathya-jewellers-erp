package com.jewellery.erp.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "Category")
public record CategoryDto(
        Long id,
        @Schema(example = "Ring") String name,
        @Schema(example = "RING") String code,
        String description,
        boolean active,
        @Schema(description = "Sub categories defined under this category") long subCategoryCount,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {}
