package com.ecomassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for resolving product image keys to public URLs. The base URL defaults
 * to {@code /images/}, which the storefront frontend serves from its static directory;
 * moving images to a CDN/blob storage later is a configuration change only.
 */
@ConfigurationProperties(prefix = "ecom.assets")
public record EcomAssetsProperties(String baseUrl) {

    public static final String DEFAULT_BASE_URL = "/images/";

    public EcomAssetsProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }
    }

    /**
     * Joins the base URL and an image key with exactly one {@code /} between them,
     * regardless of a trailing slash on the base URL or a leading slash on the key.
     *
     * @return the public image URL, or {@code null} when the key is blank
     */
    public String imageUrl(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            return null;
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String key = imageKey.startsWith("/") ? imageKey.substring(1) : imageKey;
        return base + "/" + key;
    }
}
