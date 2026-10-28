package com.ecomassistant.dto;

import com.ecomassistant.guardrails.PriceOperator;

import java.math.BigDecimal;

/**
 * The price constraint of a search interpretation, mirroring
 * {@link com.ecomassistant.guardrails.PriceConstraint} without exposing guardrail
 * internals.
 */
public record PriceConstraintDto(PriceOperator operator, BigDecimal value) {
}
