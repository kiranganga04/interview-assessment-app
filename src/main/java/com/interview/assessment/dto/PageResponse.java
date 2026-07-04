package com.interview.assessment.dto;

import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Module 8: thin, stable wrapper around Spring Data's Page so the frontend contract
 * doesn't depend on Spring's internal PageImpl JSON shape.
 */
@Data
public class PageResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static <T> PageResponse<T> from(Page<T> page) {
        PageResponse<T> response = new PageResponse<>();
        response.setContent(page.getContent());
        response.setPage(page.getNumber());
        response.setSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        return response;
    }
}
