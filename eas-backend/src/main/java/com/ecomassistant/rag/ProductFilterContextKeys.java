package com.ecomassistant.rag;

/**
 * Context keys written by the {@code QueryAnalysisGuardrailAdvisor} and read back by the
 * search service from the response context of the product-search {@code ChatClient}.
 */
public final class ProductFilterContextKeys {

    /**
     * Value: {@link com.ecomassistant.guardrails.QueryAnalysis} - the validated analysis
     * of the request, written by the query-analysis guardrail and read back by the search
     * service to build the response interpretation.
     */
    public static final String QUERY_ANALYSIS = "ecom_query_analysis";

    private ProductFilterContextKeys() {
    }
}
