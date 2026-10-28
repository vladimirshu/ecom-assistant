package com.ecomassistant.dto;

import com.ecomassistant.guardrails.QueryAnalysis;

import java.util.List;

/**
 * The client-facing interpretation of a search request, derived from the validated
 * {@link QueryAnalysis}. It carries the English search query, the negative examples, the
 * price constraint (or {@code null}) and the lowest-price flag, but omits the rejection
 * reasons and suggested prompts, which are only relevant to rejected requests.
 */
public record SearchInterpretationDto(
        String searchQuery,
        List<String> negativeExamples,
        PriceConstraintDto priceConstraint,
        boolean sortByLowestPrice) {

    /**
     * Maps a validated {@link QueryAnalysis} to its interpretation. A {@code null}
     * analysis maps to {@code null}; a {@code null} negative list maps to an empty list.
     */
    public static SearchInterpretationDto from(QueryAnalysis analysis) {
        if (analysis == null) {
            return null;
        }
        PriceConstraintDto priceConstraint = analysis.priceConstraint() == null
                ? null
                : new PriceConstraintDto(analysis.priceConstraint().operator(),
                        analysis.priceConstraint().value());
        List<String> negativeExamples =
                analysis.negativeExamples() == null ? List.of() : analysis.negativeExamples();
        return new SearchInterpretationDto(
                analysis.searchQuery(),
                negativeExamples,
                priceConstraint,
                analysis.sortByLowestPrice());
    }
}
