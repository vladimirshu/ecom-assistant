package com.ecomassistant.tools;

import lombok.Getter;

@Getter
public enum PriceOperator {
    LESS_THAN("<"),
    GREATER_THAN(">"),
    LESS_THAN_OR_EQUAL("<="),
    GREATER_THAN_OR_EQUAL(">="),
    LOWEST("lowest"),
    NOT_RELEVANT("not_relevant");

    private final String symbol;

    PriceOperator(String symbol) {
        this.symbol = symbol;
    }

}