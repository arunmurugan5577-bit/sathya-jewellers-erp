package com.jewellery.erp.shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "ShopSettings")
public record ShopSettingsDto(
        Long id,
        String shopName,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String pincode,
        String mobileNumber,
        String alternateMobileNumber,
        String email,
        @Schema(example = "33AAAAA0000A1Z5") String gstin,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {}
