package com.jewellery.erp.subcategory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create and update payload for a sub category.
 *
 * <p>{@code categoryId} is mandatory here and non-null in the database: a sub
 * category that does not belong to a category is not a thing the business has.
 */
@Schema(name = "SubCategoryRequest")
public record SubCategoryRequest(
        @Schema(example = "1", description = "Parent category; mandatory")
        @NotNull(message = "Category is required")
        Long categoryId,

        @Schema(example = "Mens Ring")
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Schema(example = "RING-M", description = "Short code, stored upper case; unique shop-wide")
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
