package com.ecomassistant.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EcomAssetsPropertiesTest {

    @Test
    void defaultsToImagesBaseUrl() {
        EcomAssetsProperties properties = new EcomAssetsProperties(null);

        assertThat(properties.baseUrl()).isEqualTo("/images/");
        assertThat(properties.imageUrl("categories/parka.webp")).isEqualTo("/images/categories/parka.webp");
    }

    @Test
    void joinsCdnBaseUrlWithoutTrailingSlash() {
        EcomAssetsProperties properties = new EcomAssetsProperties("https://cdn.example.com/catalog");

        assertThat(properties.imageUrl("categories/parka.webp"))
            .isEqualTo("https://cdn.example.com/catalog/categories/parka.webp");
    }

    @Test
    void joinsCdnBaseUrlWithTrailingSlash() {
        EcomAssetsProperties properties = new EcomAssetsProperties("https://cdn.example.com/catalog/");

        assertThat(properties.imageUrl("categories/parka.webp"))
            .isEqualTo("https://cdn.example.com/catalog/categories/parka.webp");
    }

    @Test
    void stripsLeadingSlashFromImageKey() {
        EcomAssetsProperties properties = new EcomAssetsProperties("https://cdn.example.com/catalog");

        assertThat(properties.imageUrl("/categories/parka.webp"))
            .isEqualTo("https://cdn.example.com/catalog/categories/parka.webp");
    }

    @Test
    void blankImageKeyResolvesToNull() {
        EcomAssetsProperties properties = new EcomAssetsProperties("/images/");

        assertThat(properties.imageUrl(null)).isNull();
        assertThat(properties.imageUrl("  ")).isNull();
    }
}
