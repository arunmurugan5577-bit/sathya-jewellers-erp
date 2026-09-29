package com.jewellery.erp.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "CategoryRequest")
public record CategoryRequest(
        @Schema(example = "Ring")
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Schema(example = "RING", description = "Short code, stored upper case")
        @NotBlank(message = "Code is required")
        @Size(max = 20, message = "Code must not exceed 20 characters")
        @Pattern(
                regexp = "^[A-Za-z0-9_-]+$",
                message = "Code may contain only letters, digits, underscore and hyphen")
        String code,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        @Schema(description = "Defaults to active when omitted")
        Boolean active) {}
