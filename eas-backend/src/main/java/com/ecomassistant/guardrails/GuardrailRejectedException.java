package com.ecomassistant.guardrails;

/**
 * Thrown when query analysis determines the request must be rejected. Carries the
 * {@link QueryAnalysis} so the exception handler can surface the suggested prompts to the
 * client (HTTP 422).
 */
public class GuardrailRejectedException extends RuntimeException {

    private final QueryAnalysis queryAnalysis;

    public GuardrailRejectedException(QueryAnalysis queryAnalysis) {
        super("Request rejected by query analysis: " + queryAnalysis.rejectionReasons());
        this.queryAnalysis = queryAnalysis;
    }

    public QueryAnalysis getQueryAnalysis() {
        return queryAnalysis;
    }
}
