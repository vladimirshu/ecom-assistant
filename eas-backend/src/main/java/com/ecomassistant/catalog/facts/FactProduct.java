package com.ecomassistant.catalog.facts;

import java.math.BigDecimal;
import java.util.List;

/**
 * One authored product in the facts source. The ordinary import fields (SKU, title,
 * category slug, browsing category ids, colors, sizes, price, optional image key and
 * description) mirror the CSV owned by {@code apparel-catalog-browsing}; {@code facts}
 * carries the authoring-only functional profile.
 */
public record FactProduct(
        String sku,
        String title,
        String category,
        List<String> categoryIds,
        List<String> colors,
        List<String> sizes,
        BigDecimal price,
        String imageKey,
        String description,
        Facts facts) {

    public FactProduct {
        categoryIds = categoryIds == null ? List.of() : List.copyOf(categoryIds);
        colors = colors == null ? List.of() : List.copyOf(colors);
        sizes = sizes == null ? List.of() : List.copyOf(sizes);
    }
}
