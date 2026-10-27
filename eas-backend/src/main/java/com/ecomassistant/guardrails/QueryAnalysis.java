package com.ecomassistant.guardrails;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Structured result of the single query-analysis LLM call.
 *
 * <p>The verdict fields ({@code allowed}, {@code rejectionReasons},
 * {@code suggestedPrompts}) are kept separate from the search fields
 * ({@code searchQuery}, {@code negativeExamples}, {@code priceConstraint},
 * {@code sortByLowestPrice}) so the guardrail and the query transformation can be split
 * into separate calls later without changing consumers.
 *
 * @param allowed            whether the request is a valid apparel search request
 * @param rejectionReasons   why the request was rejected; empty when allowed
 * @param suggestedPrompts   example prompts to suggest when rejected; empty when allowed
 * @param searchQuery        the compressed, English search query with price and negatives removed
 * @param negativeExamples   negative examples to exclude from the search
 * @param priceConstraint    a single price constraint, or {@code null} when there is none
 * @param sortByLowestPrice  whether to order results by ascending price
 */
public record QueryAnalysis(
        boolean allowed,
        List<String> rejectionReasons,
        List<String> suggestedPrompts,
        String searchQuery,
        List<String> negativeExamples,
        @Nullable PriceConstraint priceConstraint,
        boolean sortByLowestPrice) {
}
