package com.jewellery.erp.itemtype.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "ItemType")
public record ItemTypeDto(
        Long id,
        @Schema(example = "Gold") String name,
        @Schema(example = "GOLD") String code,
        String description,
        boolean active,
        @Schema(description = "Purities defined for this item type") long purityCount,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {}
