package com.jewellery.erp.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Explicit pagination envelope.
 *
 * <p>Spring's {@code Page} serialises its internal structure, which would tie
 * the Angular client to a Spring implementation detail. This record is the
 * contract instead.
 */
@Schema(name = "Page", description = "A page of results")
public record PageResponse<T>(
        List<T> content,
        @Schema(example = "0") int page,
        @Schema(example = "20") int size,
        @Schema(example = "137") long totalElements,
        @Schema(example = "7") int totalPages,
        boolean first,
        boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    /** Maps an entity page into a DTO page in one step. */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return from(page.map(mapper));
    }
}
