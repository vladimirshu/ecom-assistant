package com.ecomassistant.limits;

import com.ecomassistant.config.EcomLimitsProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global daily search budget: an atomic counter tied to the UTC date it belongs to. The
 * first search of a new UTC day resets it. The counter lives in process memory, so it also
 * resets on restart; the external OpenAI project budget is the hard cap (see the runbook).
 */
@Component
public class DailySearchBudget {

    public record Result(boolean allowed, long retryAfterSeconds) {
        private static Result granted() {
            return new Result(true, 0);
        }

        private static Result denied(long retryAfterSeconds) {
            return new Result(false, retryAfterSeconds);
        }
    }

    private final Clock clock;
    private final int dailyBudget;
    private final AtomicInteger counter = new AtomicInteger();
    private volatile LocalDate currentDate;

    @Autowired
    public DailySearchBudget(Clock clock, EcomLimitsProperties properties) {
        this(clock, properties.dailyBudget());
    }

    DailySearchBudget(Clock clock, int dailyBudget) {
        this.clock = clock;
        this.dailyBudget = dailyBudget;
    }

    public synchronized Result tryAcquire() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (!today.equals(currentDate)) {
            currentDate = today;
            counter.set(0);
        }
        if (counter.incrementAndGet() > dailyBudget) {
            return Result.denied(secondsUntilReset(now));
        }
        return Result.granted();
    }

    /** Seconds until the next UTC midnight, for the {@code Retry-After} header. */
    public long secondsUntilReset() {
        return secondsUntilReset(clock.instant());
    }

    private static long secondsUntilReset(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        Instant nextMidnight = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return Math.max(1, Duration.between(now, nextMidnight).getSeconds());
    }
}
