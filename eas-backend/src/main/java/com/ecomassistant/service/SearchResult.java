package com.ecomassistant.service;

import com.ecomassistant.dto.ProductDto;
import com.ecomassistant.dto.SearchInterpretationDto;

import java.util.List;

/**
 * Outcome of a successful search: the matching products (already mapped to their API
 * representation) and the interpretation of the request, ready to be placed into a
 * {@link com.ecomassistant.dto.ChatResponseDto}.
 */
public record SearchResult(List<ProductDto> products, SearchInterpretationDto interpretation) {
}
