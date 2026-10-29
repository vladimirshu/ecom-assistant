package com.ecomassistant.health;

import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

/**
 * Reports DOWN until the application has finished its startup runners. Spring Boot
 * publishes {@link ReadinessState#ACCEPTING_TRAFFIC} only after every
 * {@code CommandLineRunner} / {@code ApplicationRunner} has completed, so the health
 * endpoint stays DOWN during the first-boot catalog import and price index creation.
 */
@Component
public class ReadinessHealthIndicator extends AbstractHealthIndicator {

    private final ApplicationAvailability availability;

    public ReadinessHealthIndicator(ApplicationAvailability availability) {
        this.availability = availability;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        if (availability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC) {
            builder.up();
        } else {
            builder.down();
        }
    }
}
