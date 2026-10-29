package com.ecomassistant.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadinessHealthIndicatorTest {

    @Test
    void reportsUpWhenAcceptingTraffic() {
        ApplicationAvailability availability = mock(ApplicationAvailability.class);
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);

        Health health = new ReadinessHealthIndicator(availability).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWhenRefusingTraffic() {
        ApplicationAvailability availability = mock(ApplicationAvailability.class);
        when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);

        Health health = new ReadinessHealthIndicator(availability).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
