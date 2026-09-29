package com.jewellery.erp.hsn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "HsnCode")
public record HsnCodeDto(
        Long id,
        @Schema(example = "7113") String hsnCode,
        String description,
        @Schema(example = "3.00", description = "GST rate as an exact decimal percentage")
        BigDecimal gstPercentage,
        boolean active,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {}
