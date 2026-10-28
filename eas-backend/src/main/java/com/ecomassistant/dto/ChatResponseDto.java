package com.ecomassistant.dto;

import java.util.List;

/**
 * Result of a search request. On HTTP 200 {@code products} is populated and
 * {@code suggestedPrompts} is {@code null}; on HTTP 422 {@code products} and
 * {@code interpretation} are {@code null} and {@code suggestedPrompts} is populated.
 */
public record ChatResponseDto(
        List<ProductDto> products,
        SearchInterpretationDto interpretation,
        List<String> suggestedPrompts) {
}
