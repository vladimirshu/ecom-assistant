package com.ecomassistant.dto;

import com.ecomassistant.config.EcomAssetsProperties;
import com.ecomassistant.catalog.Product;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Maps a {@link Product} entity to its {@link ProductDto} representation: splits and
 * trims the comma-separated {@code colors} and {@code sizes}, orders sizes in canonical
 * order (unknown sizes last, in catalog order), and resolves the image key to a public
 * URL.
 */
@Component
public class ProductDtoMapper {

    private static final List<String> SIZE_ORDER =
            List.of("XXS", "XS", "S", "M", "L", "XL", "XXL", "XXXL");

    private final EcomAssetsProperties assetsProperties;

    public ProductDtoMapper(EcomAssetsProperties assetsProperties) {
        this.assetsProperties = assetsProperties;
    }

    public ProductDto toDto(Product product) {
        return new ProductDto(
                product.getSku(),
                product.getTitle(),
                product.getCategory() == null ? null : product.getCategory().displayName(),
                split(product.getColors()),
                orderSizes(split(product.getSizes())),
                product.getPrice(),
                product.getDescription(),
                assetsProperties.imageUrl(product.getImageKey()));
    }

    /**
     * Splits a comma-separated value into a trimmed, blank-free list, preserving catalog
     * order.
     */
    public List<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    /**
     * Orders sizes in canonical order {@code XXS..XXXL}, with any unknown sizes appended
     * last in their original (catalog) order.
     */
    public List<String> orderSizes(List<String> sizes) {
        List<String> known = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String size : sizes) {
            if (SIZE_ORDER.contains(size)) {
                known.add(size);
            }
            else {
                unknown.add(size);
            }
        }
        known.sort(Comparator.comparingInt(SIZE_ORDER::indexOf));
        List<String> ordered = new ArrayList<>(known);
        ordered.addAll(unknown);
        return List.copyOf(ordered);
    }
}
