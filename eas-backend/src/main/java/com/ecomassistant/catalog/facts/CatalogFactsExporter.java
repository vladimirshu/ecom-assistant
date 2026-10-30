package com.ecomassistant.catalog.facts;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Exports the authoring-facts source into the semicolon-delimited CSV format owned by
 * {@code apparel-catalog-browsing}. The export is deterministic so that the committed CSV
 * and the facts document can be compared for parity and cannot drift independently.
 *
 * <p>The exported CSV header is exactly the one the {@code ProductImportService} parses:
 * {@code sku;title;category;categoryIds;colors;sizes;price;image;description}. The
 * {@code categoryPathIds} and {@code browsingText} columns are not exported: they are
 * computed at import time from the browsing category registry, not authored.
 */
public final class CatalogFactsExporter {

    public static final String CSV_HEADER =
            "sku;title;category;categoryIds;colors;sizes;price;image;description";

    private CatalogFactsExporter() {
    }

    /** Exports the full CSV document (header plus one line per product) ending in a newline. */
    public static String toCsv(CatalogFacts facts) {
        StringBuilder out = new StringBuilder(CSV_HEADER).append('\n');
        for (FactProduct product : facts.products()) {
            out.append(toRow(product)).append('\n');
        }
        return out.toString();
    }

    /** Exports a single product as one CSV row (no trailing newline). */
    public static String toRow(FactProduct p) {
        return String.join(";",
                p.sku(),
                p.title(),
                p.category(),
                join(p.categoryIds()),
                join(p.colors()),
                join(p.sizes()),
                formatPrice(p.price()),
                p.imageKey() == null ? "" : p.imageKey(),
                p.description());
    }

    private static String join(List<String> values) {
        return values.stream().collect(Collectors.joining(","));
    }

    private static String formatPrice(BigDecimal price) {
        return price == null ? "" : price.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
