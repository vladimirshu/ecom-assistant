package com.ecomassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ecom.search")
public record EcomSearchProperties(int topK, float similarityThreshold) {
}
