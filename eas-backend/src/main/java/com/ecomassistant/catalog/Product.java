package com.ecomassistant.catalog;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * The catalog entity as authored from the import CSV and stored in Qdrant. It carries the
 * garment-style {@code category} plus the browsing membership fields used by catalog
 * listing. These browsing fields are internal to import/listing and are intentionally not
 * exposed on the public {@link com.ecomassistant.dto.ProductDto}.
 */
@Data
@NoArgsConstructor
public class Product {

    private String sku;
    private String title;
    private ProductCategory category;
    private String imageKey;
    private String colors;
    private String sizes;
    private BigDecimal price;
    private String description;

    /** Direct browsing category ids authored in the CSV. */
    private List<String> categoryIds;

    /** Ancestor-inclusive browsing ids computed at import (direct ids plus their ancestors). */
    private List<String> categoryPathIds;

    /** The catalog revision this product was imported under. */
    private String catalogRevision;

    /** Readable audience/ancestry text used in embedded search content (not a payload field). */
    private String browsingText;
}
