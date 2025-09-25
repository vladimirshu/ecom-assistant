package com.ecomassistant.tools;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom tools callback because of flaws in the early Spring AI versions.
 * */
@Getter @Setter
public class AssistantToolsCallback {
    private boolean isValidPrompt = true;
    private boolean sortByPriceAsc;
    private boolean filterByPrice;
    private String filterProductsByPriceValue;
    private PriceOperator filterProductsByPriceOperator;
    private final List<String> negativeExamples = new ArrayList<>();
    private final List<String> suggestedPrompts = new ArrayList<>();

    public void addSuggestedPrompt(String prompt) {
        suggestedPrompts.add(prompt);
    }

    public void addNegativeExamples(List<String> negativeExamples) {
        this.negativeExamples.addAll(negativeExamples);
    }

}
