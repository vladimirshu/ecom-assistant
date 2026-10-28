package com.ecomassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "ecom.memory")
public record EcomMemoryProperties(
        @DefaultValue("24h") Duration inactivityTimeout,
        @DefaultValue("1h") Duration cleanupInterval) {
}
