package com.ecomassistant.catalog;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA {@link AttributeConverter} that persists a {@link ProductCategory} as its slug.
 * Unknown slugs (which should never occur, as import rejects them) map to {@code null}.
 */
@Converter
public class ProductCategoryConverter implements AttributeConverter<ProductCategory, String> {

    @Override
    public String convertToDatabaseColumn(ProductCategory attribute) {
        return attribute == null ? null : attribute.slug();
    }

    @Override
    public ProductCategory convertToEntityAttribute(String dbData) {
        return ProductCategory.fromSlug(dbData);
    }
}
