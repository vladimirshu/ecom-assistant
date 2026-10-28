package com.ecomassistant.catalog;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
}
