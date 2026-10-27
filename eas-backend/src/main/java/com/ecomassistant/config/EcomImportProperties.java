package com.ecomassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ecom.import")
public record EcomImportProperties(String sampleFile) {
}
