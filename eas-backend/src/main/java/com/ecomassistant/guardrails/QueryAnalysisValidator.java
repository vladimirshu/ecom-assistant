package com.ecomassistant.guardrails;

import com.ecomassistant.exception.IllegalAssistantResponseException;
import com.ecomassistant.validation.PlainTextPolicy;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deterministic semantic validation of a {@link QueryAnalysis} after schema validation.
 *
 * <p>When {@link QueryAnalysis#allowed()} is {@code false}, a
 * {@link GuardrailRejectedException} is thrown carrying a sanitized copy of the analysis:
 * suggested prompts and rejection reasons that are not plain text or exceed their length
 * are dropped, suggested prompts are capped at {@link #MAX_SUGGESTED_PROMPTS}, and a
 * generic suggested prompt is added when none remain. When the analysis is allowed but
 * violates a semantic rule (blank or over-long query, non-plain-text field, too many
 * suggestions or negatives, non-positive price), an
 * {@link IllegalAssistantResponseException} is thrown.
 */
@Component
public class QueryAnalysisValidator {

    static final int MAX_SEARCH_QUERY_LENGTH = 500;
    static final int MAX_SUGGESTED_PROMPTS = 5;
    static final int MAX_SUGGESTED_PROMPT_LENGTH = 300;
    static final int MAX_NEGATIVE_EXAMPLES = 10;
    static final int MAX_NEGATIVE_EXAMPLE_LENGTH = 300;
    static final int MAX_REJECTION_REASON_LENGTH = 500;

    public static final String GENERIC_SUGGESTED_PROMPT =
            "Please describe the clothing item you are looking for, for example: "
                    + "\"a blue winter jacket under 200 euro\".";

    public QueryAnalysis validate(QueryAnalysis analysis) {
        if (analysis == null) {
            throw illegal("Query analysis must not be null", "null");
        }

        if (!analysis.allowed()) {
            throw new GuardrailRejectedException(sanitizeRejection(analysis));
        }

        validateAllowed(analysis);
        return analysis;
    }

    private void validateAllowed(QueryAnalysis analysis) {
        PlainTextPolicy.requirePlainText(analysis.searchQuery(), MAX_SEARCH_QUERY_LENGTH);

        for (String reason : safeList(analysis.rejectionReasons())) {
            PlainTextPolicy.requirePlainText(reason, MAX_REJECTION_REASON_LENGTH);
        }
        for (String prompt : safeList(analysis.suggestedPrompts())) {
            PlainTextPolicy.requirePlainText(prompt, MAX_SUGGESTED_PROMPT_LENGTH);
        }
        for (String example : safeList(analysis.negativeExamples())) {
            PlainTextPolicy.requirePlainText(example, MAX_NEGATIVE_EXAMPLE_LENGTH);
        }

        List<String> suggestedPrompts = safeList(analysis.suggestedPrompts());
        if (suggestedPrompts.size() > MAX_SUGGESTED_PROMPTS) {
            throw illegal("Suggested prompts exceed the maximum of " + MAX_SUGGESTED_PROMPTS, analysis);
        }
        if (safeList(analysis.negativeExamples()).size() > MAX_NEGATIVE_EXAMPLES) {
            throw illegal("Negative examples exceed the maximum of " + MAX_NEGATIVE_EXAMPLES, analysis);
        }

        PriceConstraint priceConstraint = analysis.priceConstraint();
        if (priceConstraint != null && (priceConstraint.value() == null
                || priceConstraint.value().signum() <= 0)) {
            throw illegal("Price constraint value must be positive", analysis);
        }
    }

    private static QueryAnalysis sanitizeRejection(QueryAnalysis analysis) {
        List<String> suggestedPrompts = plainTextOnly(analysis.suggestedPrompts(), MAX_SUGGESTED_PROMPT_LENGTH)
                .stream()
                .limit(MAX_SUGGESTED_PROMPTS)
                .toList();
        if (suggestedPrompts.isEmpty()) {
            suggestedPrompts = List.of(GENERIC_SUGGESTED_PROMPT);
        }
        List<String> rejectionReasons = plainTextOnly(analysis.rejectionReasons(), MAX_REJECTION_REASON_LENGTH);
        return new QueryAnalysis(false, rejectionReasons, suggestedPrompts, "", List.of(), null, false);
    }

    private static List<String> plainTextOnly(List<String> texts, int maxLength) {
        return safeList(texts).stream()
                .filter(text -> isPlainText(text, maxLength))
                .toList();
    }

    private static boolean isPlainText(String text, int maxLength) {
        try {
            PlainTextPolicy.requirePlainText(text, maxLength);
            return true;
        } catch (IllegalAssistantResponseException e) {
            return false;
        }
    }

    private static List<String> safeList(List<String> list) {
        return list == null ? List.of() : list;
    }

    private static IllegalAssistantResponseException illegal(String message, QueryAnalysis analysis) {
        return new IllegalAssistantResponseException(message, String.valueOf(analysis));
    }

    private static IllegalAssistantResponseException illegal(String message, String response) {
        return new IllegalAssistantResponseException(message, response);
    }
}
