package com.ecomassistant.limits;

import com.ecomassistant.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DailySearchBudgetTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T23:59:00Z"));

    @Test
    void budgetExhaustedAfterDailyLimit() {
        DailySearchBudget budget = new DailySearchBudget(clock, 2);

        assertThat(budget.tryAcquire().allowed()).isTrue();
        assertThat(budget.tryAcquire().allowed()).isTrue();

        DailySearchBudget.Result rejected = budget.tryAcquire();
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 24 * 3600L);
    }

    @Test
    void budgetResetsAtUtcMidnight() {
        DailySearchBudget budget = new DailySearchBudget(clock, 1);

        assertThat(budget.tryAcquire().allowed()).isTrue();
        assertThat(budget.tryAcquire().allowed()).isFalse();

        clock.set(Instant.parse("2026-01-02T00:00:01Z"));

        assertThat(budget.tryAcquire().allowed()).isTrue();
    }

    @Test
    void secondsUntilResetCountsDownToNextMidnight() {
        DailySearchBudget budget = new DailySearchBudget(clock, 10);

        // 23:59:00 UTC → next midnight is 60 seconds away.
        assertThat(budget.secondsUntilReset()).isEqualTo(60);
    }
}
