package com.jewellery.erp.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * Dashboard metrics.
 *
 * <p>Every field is nullable, and a null means "you may not see this", not
 * "zero". A user without USER_VIEW receives no user count at all rather than a
 * count they are not entitled to - and because null fields are omitted from the
 * JSON, the UI can simply render the cards it is given.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "DashboardSummary")
public record DashboardSummaryDto(
        @Schema(example = "1284") Long totalItems,
        @Schema(example = "1190") Long activeItems,
        @Schema(example = "4210.750", description = "Gross weight of active stock, in grams")
        BigDecimal activeWeightGrams,
        @Schema(example = "7") Long totalCategories,
        @Schema(example = "5") Long totalUsers,
        @Schema(example = "4") Long activeUsers) {}
