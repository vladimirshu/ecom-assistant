package com.ecomassistant.limits;

/**
 * Thrown when a valid search is rejected by the rate limits or the daily budget. The
 * controller's global exception handler maps it to HTTP 429 with a {@code Retry-After}
 * header and a {@code {code, message}} JSON body.
 */
public class SearchRejectedException extends RuntimeException {

    public static final String CODE_RATE_LIMITED = "RATE_LIMITED";
    public static final String CODE_DAILY_BUDGET_EXHAUSTED = "DAILY_BUDGET_EXHAUSTED";

    private final String code;
    private final long retryAfterSeconds;

    public SearchRejectedException(String code, long retryAfterSeconds) {
        super(messageFor(code, retryAfterSeconds));
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getCode() {
        return code;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    private static String messageFor(String code, long retryAfterSeconds) {
        if (CODE_RATE_LIMITED.equals(code)) {
            return "Too many searches. Retry after " + retryAfterSeconds + " seconds.";
        }
        if (CODE_DAILY_BUDGET_EXHAUSTED.equals(code)) {
            return "Daily search budget exhausted. Retry after " + retryAfterSeconds + " seconds.";
        }
        return "Search rejected.";
    }
}
