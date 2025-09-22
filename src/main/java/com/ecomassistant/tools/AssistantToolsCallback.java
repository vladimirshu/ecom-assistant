package com.ecomassistant.tools;

import lombok.Getter;
import lombok.Setter;

/**
 * Custom tools callback because of flaws in the early Spring AI versions.
 * */
public class AssistantToolsCallback {
    @Setter
    @Getter
    private boolean isValidPrompt = true;
    @Setter
    @Getter
    private boolean sortByPriceAsc;
    @Setter
    @Getter
    private boolean filterByPrice;
    private final StringBuilder suggestedPromptBuilder = new StringBuilder();

    @Getter
    private String filterProductsByPriceValue;
    @Getter
    private PriceOperator filterProductsByPriceOperator;

    public void appendSuggestedPrompt(String prompt) {
        suggestedPromptBuilder.append("  - ").append(prompt);
    }

    public String getSuggestedPrompt() {
        return suggestedPromptBuilder.toString();
    }

    public void setFilterProductsByPriceCall(String price, PriceOperator operator) {
        this.filterProductsByPriceValue = price;
        this.filterProductsByPriceOperator = operator;
    }

}
