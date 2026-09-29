package com.jewellery.erp.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Customer picker option: enough to recognise the right person at the counter. */
@Schema(name = "CustomerSummary")
public record CustomerSummaryDto(
        Long id,
        String customerCode,
        String fullName,
        String mobileNumber,
        String formattedAddress) {}
