package com.ecomassistant.rag;

import java.util.List;

/**
 * Structured output of the product-filtering LLM call: the SKUs of the retrieved
 * candidates that actually match the customer's request.
 *
 * <p>The LLM selects; it does not order. The order in which the selected SKUs are
 * returned to the client is defined later by {@code SkuOutputGuardrail}, which preserves
 * retrieval order and ignores the order produced by the model.
 *
 * @param skus SKUs selected by the product-filtering call
 */
public record ProductSelection(List<String> skus) {
}
