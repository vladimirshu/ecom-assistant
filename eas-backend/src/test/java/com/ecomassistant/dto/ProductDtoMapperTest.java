package com.ecomassistant.dto;

import com.ecomassistant.catalog.ProductCategory;
import com.ecomassistant.config.EcomAssetsProperties;
import com.ecomassistant.catalog.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductDtoMapperTest {

    private final ProductDtoMapper mapper = new ProductDtoMapper(new EcomAssetsProperties("/images/"));

    @Test
    void ordersSizesInCanonicalOrder() {
        assertThat(mapper.orderSizes(List.of("M", "XS", "S")))
            .containsExactly("XS", "S", "M");
    }

    @Test
    void putsUnknownSizesLastInCatalogOrder() {
        assertThat(mapper.orderSizes(List.of("M", "Petite", "XS", "Tall")))
            .containsExactly("XS", "M", "Petite", "Tall");
    }

    @Test
    void splitsColorsIntoLtrimmedArrayPreservingOrder() {
        assertThat(mapper.split("navy, red ,  beige"))
            .containsExactly("navy", "red", "beige");
    }

    @Test
    void blankColorsYieldsEmptyList() {
        assertThat(mapper.split("  ")).isEmpty();
        assertThat(mapper.split(null)).isEmpty();
    }

    @Test
    void mapsEntityToDtoWithCanonicalSizesAndResolvedImageUrl() {
        Product product = new Product();
        product.setSku("JKT0001");
        product.setTitle("Metro Commuter Rain Jacket");
        product.setCategory(ProductCategory.RAIN);
        product.setImageKey("categories/rain.webp");
        product.setColors("navy, red");
        product.setSizes("M,XS,S");
        product.setPrice(new BigDecimal("262.73"));
        product.setDescription("Urban explorer jacket.");

        ProductDto dto = mapper.toDto(product);

        assertThat(dto.sku()).isEqualTo("JKT0001");
        assertThat(dto.title()).isEqualTo("Metro Commuter Rain Jacket");
        assertThat(dto.category()).isEqualTo("Rain Jackets");
        assertThat(dto.colors()).containsExactly("navy", "red");
        assertThat(dto.sizes()).containsExactly("XS", "S", "M");
        assertThat(dto.price()).isEqualByComparingTo(new BigDecimal("262.73"));
        assertThat(dto.description()).isEqualTo("Urban explorer jacket.");
        assertThat(dto.imageUrl()).isEqualTo("/images/categories/rain.webp");
    }
}
