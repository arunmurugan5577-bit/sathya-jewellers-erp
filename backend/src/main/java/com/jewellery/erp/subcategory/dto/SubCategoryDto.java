package com.jewellery.erp.subcategory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "SubCategory")
public record SubCategoryDto(
        Long id,
        Long categoryId,
        @Schema(example = "Ring") String categoryName,
        @Schema(example = "Mens Ring") String name,
        @Schema(example = "RING-M") String code,
        String description,
        boolean active,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {}
