package com.ecomassistant.limits;

import com.ecomassistant.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ClientRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));

    private ClientRateLimiter limiter;

    @BeforeEach
    void setUp() {
        // Isolate each behaviour: a huge counterpart capacity means only the limit under
        // test can trigger.
        limiter = new ClientRateLimiter(clock, 5, 20, Duration.ofHours(1));
    }

    @Test
    void minuteLimitRejectsSixthSearchWithinAMinute() {
        ClientRateLimiter minuteOnly = new ClientRateLimiter(clock, 5, 1000, Duration.ofHours(1));
        for (int i = 0; i < 5; i++) {
            assertThat(minuteOnly.tryAcquire("client").allowed()).isTrue();
        }

        ClientRateLimiter.Result sixth = minuteOnly.tryAcquire("client");

        assertThat(sixth.allowed()).isFalse();
        assertThat(sixth.retryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    void hourLimitRejectsTwentyFirstSearchWithinAnHour() {
        ClientRateLimiter hourOnly = new ClientRateLimiter(clock, 1000, 20, Duration.ofHours(1));
        for (int i = 0; i < 20; i++) {
            assertThat(hourOnly.tryAcquire("client").allowed()).isTrue();
        }

        ClientRateLimiter.Result twentyFirst = hourOnly.tryAcquire("client");

        assertThat(twentyFirst.allowed()).isFalse();
        assertThat(twentyFirst.retryAfterSeconds()).isBetween(1L, 3600L);
    }

    @Test
    void recoversAfterRetryAfterPeriod() {
        ClientRateLimiter minuteOnly = new ClientRateLimiter(clock, 5, 1000, Duration.ofHours(1));
        for (int i = 0; i < 5; i++) {
            minuteOnly.tryAcquire("client");
        }
        ClientRateLimiter.Result rejected = minuteOnly.tryAcquire("client");
        assertThat(rejected.allowed()).isFalse();

        clock.advance(Duration.ofSeconds(rejected.retryAfterSeconds()));

        assertThat(minuteOnly.tryAcquire("client").allowed()).isTrue();
    }

    @Test
    void clientsAreIndependent() {
        ClientRateLimiter minuteOnly = new ClientRateLimiter(clock, 5, 1000, Duration.ofHours(1));
        for (int i = 0; i < 5; i++) {
            minuteOnly.tryAcquire("a");
        }
        assertThat(minuteOnly.tryAcquire("a").allowed()).isFalse();

        assertThat(minuteOnly.tryAcquire("b").allowed()).isTrue();
    }

    @Test
    void releaseReturnsAToken() {
        ClientRateLimiter minuteOnly = new ClientRateLimiter(clock, 5, 1000, Duration.ofHours(1));
        for (int i = 0; i < 5; i++) {
            minuteOnly.tryAcquire("client");
        }
        assertThat(minuteOnly.tryAcquire("client").allowed()).isFalse();

        minuteOnly.release("client");

        assertThat(minuteOnly.tryAcquire("client").allowed()).isTrue();
    }

    @Test
    void idleSweepDropsInactiveClients() {
        limiter.tryAcquire("idle");
        clock.advance(Duration.ofHours(2));
        limiter.sweepIdleClients();

        assertThat(limiter.size()).isZero();
    }

    @Test
    void idleSweepKeepsRecentlyActiveClients() {
        limiter.tryAcquire("idle");
        clock.advance(Duration.ofMinutes(30));
        limiter.tryAcquire("active");
        clock.advance(Duration.ofMinutes(31));

        limiter.sweepIdleClients();

        assertThat(limiter.size()).isEqualTo(1);
    }
}
