package com.ecomassistant.rag;

/**
 * Context keys written by the {@code QueryAnalysisGuardrailAdvisor} and read by
 * {@link ProductFilterQueryAugmenter} from the request context of the product-search
 * {@code ChatClient}.
 */
public final class ProductFilterContextKeys {

    /** Value: {@code String} - the customer's original message, before compression. */
    public static final String ORIGINAL_MESSAGE = "ecom_original_message";

    /** Value: {@link com.ecomassistant.guardrails.PriceConstraint} or {@code null}. */
    public static final String PRICE_CONSTRAINT = "ecom_price_constraint";

    private ProductFilterContextKeys() {
    }
}
