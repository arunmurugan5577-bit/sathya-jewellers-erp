package com.jewellery.erp.itemtype.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create and update payload for an item type.
 *
 * <p>The same record serves both verbs: the fields a client may set are
 * identical, and two near-identical records would drift apart.
 */
@Schema(name = "ItemTypeRequest")
public record ItemTypeRequest(
        @Schema(example = "Gold")
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Schema(example = "GOLD", description = "Short code, stored upper case")
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
