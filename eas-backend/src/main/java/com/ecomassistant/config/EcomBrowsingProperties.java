package com.ecomassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuration for catalog browsing: where the agreed browsing category tree lives
 * (backend copy), where the frontend's editable tree lives for parity checks, and the
 * cursor/listing defaults.
 *
 * @param categoriesFile         classpath resource holding the backend browsing tree
 * @param frontendCategoriesFile filesystem path to the frontend category tree (parity
 *                               checks only; optional and not read at runtime)
 * @param cursorTtl              how long an issued cursor stays valid (at least 30 minutes)
 * @param defaultLimit           page size used when no {@code limit} is supplied
 */
@ConfigurationProperties(prefix = "ecom.browsing")
public record EcomBrowsingProperties(
        @DefaultValue("catalog/browsing-categories.json") String categoriesFile,
        @DefaultValue("") String frontendCategoriesFile,
        @DefaultValue("PT30M") Duration cursorTtl,
        @DefaultValue("24") int defaultLimit) {
}
