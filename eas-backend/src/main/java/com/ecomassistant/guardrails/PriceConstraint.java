package com.ecomassistant.guardrails;

import java.math.BigDecimal;

/**
 * A single price constraint extracted from a search request.
 *
 * @param operator comparison operator applied to {@code price}
 * @param value    the price boundary
 */
public record PriceConstraint(PriceOperator operator, BigDecimal value) {
}
