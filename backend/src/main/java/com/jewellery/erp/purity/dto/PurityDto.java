package com.jewellery.erp.purity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "Purity")
public record PurityDto(
        Long id,
        Long itemTypeId,
        @Schema(example = "Gold") String itemTypeName,
        @Schema(example = "22K / 916") String name,
        @Schema(example = "916.000", description = "Fineness in parts per thousand")
        BigDecimal purityValue,
        String description,
        boolean active,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {}
