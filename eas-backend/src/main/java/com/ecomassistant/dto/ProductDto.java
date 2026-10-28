package com.ecomassistant.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * API representation of a product. Unlike the {@link com.ecomassistant.entity.Product}
 * entity, {@code colors} and {@code sizes} are arrays (sizes in canonical order), and the
 * internal image key is resolved to a public {@code imageUrl}. {@code category} is the
 * human-readable category name (e.g. {@code "Puffer Jackets"}).
 */
public record ProductDto(
        String sku,
        String title,
        String category,
        List<String> colors,
        List<String> sizes,
        BigDecimal price,
        String description,
        String imageUrl) {
}
