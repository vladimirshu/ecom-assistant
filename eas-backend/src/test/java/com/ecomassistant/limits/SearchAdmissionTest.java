package com.ecomassistant.limits;

import com.ecomassistant.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchAdmissionTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T23:59:00Z"));

    @Test
    void clientLimitRejectionThrowsRateLimited() {
        SearchAdmission admission = new SearchAdmission(
                new ClientRateLimiter(clock, 1, 1000, Duration.ofHours(1)),
                new DailySearchBudget(clock, 1000));

        admission.admit("client");

        assertThatThrownBy(() -> admission.admit("client"))
                .isInstanceOfSatisfying(SearchRejectedException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(SearchRejectedException.CODE_RATE_LIMITED);
                    assertThat(e.getRetryAfterSeconds()).isBetween(1L, 60L);
                });
    }

    @Test
    void budgetRejectionThrowsDailyBudgetExhausted() {
        SearchAdmission admission = new SearchAdmission(
                new ClientRateLimiter(clock, 1000, 1000, Duration.ofHours(1)),
                new DailySearchBudget(clock, 1));

        admission.admit("client");

        assertThatThrownBy(() -> admission.admit("client"))
                .isInstanceOfSatisfying(SearchRejectedException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(SearchRejectedException.CODE_DAILY_BUDGET_EXHAUSTED);
                    assertThat(e.getRetryAfterSeconds()).isBetween(1L, 24 * 3600L);
                });
    }

    @Test
    void clientCodeTakesPrecedenceWhenBothLimitsAreHit() {
        // A client at its own minute limit must see RATE_LIMITED, not the budget code.
        SearchAdmission admission = new SearchAdmission(
                new ClientRateLimiter(clock, 1, 1000, Duration.ofHours(1)),
                new DailySearchBudget(clock, 1));

        admission.admit("client"); // minute 1 -> 0 and budget 1/1: both now at their limit

        assertThatThrownBy(() -> admission.admit("client"))
                .isInstanceOfSatisfying(SearchRejectedException.class, e ->
                        assertThat(e.getCode()).isEqualTo(SearchRejectedException.CODE_RATE_LIMITED));
    }

    @Test
    void budgetRejectionReturnsTheClientToken() {
        ClientRateLimiter limiter = mock(ClientRateLimiter.class);
        when(limiter.tryAcquire("client")).thenReturn(new ClientRateLimiter.Result(true, 0));
        SearchAdmission admission = new SearchAdmission(limiter, new DailySearchBudget(clock, 0));

        assertThatThrownBy(() -> admission.admit("client"))
                .isInstanceOf(SearchRejectedException.class);

        verify(limiter).release("client");
    }

    @Test
    void budgetRejectionDoesNotReduceClientQuota() {
        ClientRateLimiter limiter = new ClientRateLimiter(clock, 2, 1000, Duration.ofHours(1));
        SearchAdmission admission = new SearchAdmission(limiter, new DailySearchBudget(clock, 1));

        admission.admit("client");                       // minute 2 -> 1, budget 1/1
        assertThatThrownBy(() -> admission.admit("client"))  // minute 1 -> 0, budget rejects, token returned 0 -> 1
                .isInstanceOf(SearchRejectedException.class);

        // The rejected search returned its token: the client still has one minute token left.
        assertThat(limiter.tryAcquire("client").allowed()).isTrue();
    }
}
