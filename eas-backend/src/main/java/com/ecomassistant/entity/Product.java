package com.ecomassistant.entity;

import com.ecomassistant.catalog.ProductCategory;
import com.ecomassistant.catalog.ProductCategoryConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
public class Product {

    @Id
    @Column(name = "sku", length = 100, nullable = false, unique = true)
    private String sku;

    @Column(name = "title", length = 80)
    private String title;

    @Column(name = "category", length = 50)
    @Convert(converter = ProductCategoryConverter.class)
    private ProductCategory category;

    @Column(name = "image_key", length = 255)
    private String imageKey;

    @Column(name = "colors", length = 50)
    private String colors;

    @Column(name = "sizes", length = 50)
    private String sizes;

    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
