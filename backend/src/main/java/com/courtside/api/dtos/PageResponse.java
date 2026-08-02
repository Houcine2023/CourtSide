package com.courtside.api.dtos;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * A stable pagination contract.
 *
 * Why not return Spring's Page directly? Its JSON shape is an internal detail that
 * has changed between Spring versions (Spring even warns about serialising PageImpl).
 * Our clients get a shape WE own and control.
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean last
) {
    public static <E, T> PageResponse<T> from(Page<E> page, java.util.function.Function<E, T> mapper) {
        return new PageResponse<>(
            page.getContent().stream().map(mapper).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isLast()
        );
    }
}
