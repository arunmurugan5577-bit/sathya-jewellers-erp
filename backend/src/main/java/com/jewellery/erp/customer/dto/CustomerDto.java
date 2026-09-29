package com.jewellery.erp.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "Customer")
public record CustomerDto(
        Long id,
        @Schema(example = "CUS-000001") String customerCode,
        @Schema(example = "Kavitha Sivaraj") String fullName,
        String mobileNumber,
        String email,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String pincode,
        String gstin,
        String pan,
        @Schema(description = "Single-line address as printed on bills") String formattedAddress,
        boolean active,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {}
