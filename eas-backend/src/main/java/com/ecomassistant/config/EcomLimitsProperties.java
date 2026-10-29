package com.ecomassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Search rate-limit and daily-budget configuration. All values are defaults that the
 * owner can change in {@code application.yml} without a code change.
 *
 * @param perMinute        accepted searches allowed per client per minute
 * @param perHour          accepted searches allowed per client per hour
 * @param dailyBudget      accepted searches allowed per UTC day across all clients
 * @param trustedProxyHops number of trusted proxies (from the right of {@code X-Forwarded-For})
 */
@ConfigurationProperties(prefix = "ecom.limits")
public record EcomLimitsProperties(
        @DefaultValue("5") int perMinute,
        @DefaultValue("20") int perHour,
        @DefaultValue("300") int dailyBudget,
        @DefaultValue("1") int trustedProxyHops) {
}
