package com.ecomassistant.limits;

import com.ecomassistant.config.EcomLimitsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-client token buckets: one minute bucket (capacity {@code perMinute}, refill
 * {@code perMinute} per minute) and one hour bucket (capacity {@code perHour}, refill
 * {@code perHour} per hour). A search consumes one token from both buckets; a request
 * rejected by the daily budget returns its token via {@link #release(String)} so it does
 * not eat the client's quota.
 *
 * <p>State is in process memory and resets on restart. A scheduled sweep drops clients
 * that have been idle for more than an hour so the map does not grow without bound.
 */
@Component
public class ClientRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(ClientRateLimiter.class);

    private static final Duration IDLE_TIMEOUT = Duration.ofHours(1);
    private static final long SWEEP_INTERVAL_MILLIS = 3_600_000L;

    public record Result(boolean allowed, long retryAfterSeconds) {
        private static Result granted() {
            return new Result(true, 0);
        }

        private static Result denied(long retryAfterSeconds) {
            return new Result(false, retryAfterSeconds);
        }
    }

    private final Clock clock;
    private final int minuteCapacity;
    private final int hourCapacity;
    private final double minuteRefillPerSecond;
    private final double hourRefillPerSecond;
    private final Duration idleTimeout;

    private final ConcurrentHashMap<String, ClientState> clients = new ConcurrentHashMap<>();

    @Autowired
    public ClientRateLimiter(Clock clock, EcomLimitsProperties properties) {
        this(clock, properties.perMinute(), properties.perHour(), IDLE_TIMEOUT);
    }

    ClientRateLimiter(Clock clock, int minuteCapacity, int hourCapacity, Duration idleTimeout) {
        this.clock = clock;
        this.minuteCapacity = minuteCapacity;
        this.hourCapacity = hourCapacity;
        this.minuteRefillPerSecond = minuteCapacity / 60.0;
        this.hourRefillPerSecond = hourCapacity / 3600.0;
        this.idleTimeout = idleTimeout;
    }

    private static final class ClientState {
        private double minuteTokens;
        private double hourTokens;
        private Instant minuteLastRefill;
        private Instant hourLastRefill;
        private Instant lastAccess;

        private ClientState(int minuteCapacity, int hourCapacity, Instant now) {
            this.minuteTokens = minuteCapacity;
            this.hourTokens = hourCapacity;
            this.minuteLastRefill = now;
            this.hourLastRefill = now;
            this.lastAccess = now;
        }
    }

    /**
     * Attempts to consume one search for the given client. On success both buckets are
     * decremented; on rejection the returned {@code retryAfterSeconds} is the number of
     * seconds until the client is allowed again (the later of the two bucket recoveries).
     */
    public Result tryAcquire(String clientKey) {
        ClientState state = clients.computeIfAbsent(clientKey,
                k -> new ClientState(minuteCapacity, hourCapacity, clock.instant()));
        synchronized (state) {
            Instant now = clock.instant();
            refill(state, now);
            state.lastAccess = now;

            long minuteWait = state.minuteTokens >= 1.0 ? 0
                    : secondsFor(state.minuteTokens, minuteRefillPerSecond);
            long hourWait = state.hourTokens >= 1.0 ? 0
                    : secondsFor(state.hourTokens, hourRefillPerSecond);

            if (minuteWait > 0 || hourWait > 0) {
                return Result.denied(Math.max(minuteWait, hourWait));
            }
            state.minuteTokens -= 1.0;
            state.hourTokens -= 1.0;
            return Result.granted();
        }
    }

    /** Returns a previously acquired token, e.g. when the daily budget rejects the search. */
    public void release(String clientKey) {
        ClientState state = clients.get(clientKey);
        if (state == null) {
            return;
        }
        synchronized (state) {
            Instant now = clock.instant();
            refill(state, now);
            state.minuteTokens = Math.min(minuteCapacity, state.minuteTokens + 1.0);
            state.hourTokens = Math.min(hourCapacity, state.hourTokens + 1.0);
            state.lastAccess = now;
        }
    }

    @Scheduled(fixedDelay = SWEEP_INTERVAL_MILLIS)
    public void sweepIdleClients() {
        Instant now = clock.instant();
        int before = clients.size();
        clients.entrySet().removeIf(entry -> isIdle(entry.getValue(), now));
        int removed = before - clients.size();
        if (removed > 0) {
            log.debug("Removed {} idle rate-limit clients", removed);
        }
    }

    /** Number of tracked clients, for the idle-sweep test. */
    int size() {
        return clients.size();
    }

    private void refill(ClientState state, Instant now) {
        state.minuteTokens = Math.min(minuteCapacity,
                state.minuteTokens + secondsBetween(state.minuteLastRefill, now) * minuteRefillPerSecond);
        state.minuteLastRefill = now;
        state.hourTokens = Math.min(hourCapacity,
                state.hourTokens + secondsBetween(state.hourLastRefill, now) * hourRefillPerSecond);
        state.hourLastRefill = now;
    }

    private boolean isIdle(ClientState state, Instant now) {
        synchronized (state) {
            return !state.lastAccess.plus(idleTimeout).isAfter(now);
        }
    }

    private static long secondsFor(double tokens, double refillPerSecond) {
        double needed = 1.0 - tokens;
        return (long) Math.ceil(needed / refillPerSecond);
    }

    private static double secondsBetween(Instant from, Instant to) {
        return Duration.between(from, to).toNanos() / 1_000_000_000.0;
    }
}
