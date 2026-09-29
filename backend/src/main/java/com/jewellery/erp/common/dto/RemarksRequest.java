package com.jewellery.erp.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Updates the free-text remarks on an issued document - the only part of it that may change. */
@Schema(name = "RemarksRequest")
public record RemarksRequest(
        @Size(max = 500, message = "Remarks must not exceed 500 characters") String remarks) {}
