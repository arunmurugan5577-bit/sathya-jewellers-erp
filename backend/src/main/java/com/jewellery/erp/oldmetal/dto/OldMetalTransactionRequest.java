package com.jewellery.erp.oldmetal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "OldMetalTransactionRequest")
public record OldMetalTransactionRequest(
        @NotNull(message = "Customer is required")
        Long customerId,

        @Schema(description = "Defaults to today in the shop's time zone; may not be in the future")
        LocalDate transactionDate,

        @NotEmpty(message = "Add at least one item")
        @Size(max = 50, message = "A purchase bill may have at most 50 items")
        List<@Valid OldMetalItemRequest> items,

        @Size(max = 500, message = "Remarks must not exceed 500 characters")
        String remarks) {}
