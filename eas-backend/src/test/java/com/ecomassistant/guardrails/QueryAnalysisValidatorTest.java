package com.ecomassistant.guardrails;

import com.ecomassistant.exception.IllegalAssistantResponseException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryAnalysisValidatorTest {

    private final QueryAnalysisValidator validator = new QueryAnalysisValidator();

    @Test
    void validAllowedAnalysisReturnsUnchanged() {
        QueryAnalysis analysis = allowed("winter jacket", List.of("wool"),
                new PriceConstraint(PriceOperator.LESS_THAN, new BigDecimal("200")), false);

        assertThat(validator.validate(analysis)).isSameAs(analysis);
    }

    @Test
    void allowedWithoutPriceAndNegativesReturnsUnchanged() {
        QueryAnalysis analysis = allowed("blue cotton jacket", List.of(), null, false);

        assertThat(validator.validate(analysis)).isSameAs(analysis);
    }

    @Test
    void rejectedAnalysisThrowsGuardrailRejectedException() {
        QueryAnalysis analysis = rejected(List.of("not a clothing search"),
                List.of("winter jacket under 200 euro"));

        assertThatThrownBy(() -> validator.validate(analysis))
            .isInstanceOf(GuardrailRejectedException.class)
            .satisfies(ex -> {
                GuardrailRejectedException rejected = (GuardrailRejectedException) ex;
                assertThat(rejected.getQueryAnalysis().suggestedPrompts())
                    .containsExactly("winter jacket under 200 euro");
            });
    }

    @Test
    void rejectionWithoutSuggestionsGetsGenericPrompt() {
        QueryAnalysis analysis = rejected(List.of("irrelevant"), List.of());

        assertThatThrownBy(() -> validator.validate(analysis))
            .isInstanceOf(GuardrailRejectedException.class)
            .satisfies(ex -> {
                GuardrailRejectedException rejected = (GuardrailRejectedException) ex;
                assertThat(rejected.getQueryAnalysis().suggestedPrompts())
                    .containsExactly(QueryAnalysisValidator.GENERIC_SUGGESTED_PROMPT);
            });
    }

    @Test
    void rejectionDropsNonPlainTextAndOverLongSuggestions() {
        QueryAnalysis analysis = rejected(List.of("irrelevant", "bad\u0007reason"),
                List.of("winter jacket", "bad\u0007prompt", "a".repeat(301), " "));

        assertThatThrownBy(() -> validator.validate(analysis))
            .isInstanceOf(GuardrailRejectedException.class)
            .satisfies(ex -> {
                QueryAnalysis sanitized = ((GuardrailRejectedException) ex).getQueryAnalysis();
                assertThat(sanitized.suggestedPrompts()).containsExactly("winter jacket");
                assertThat(sanitized.rejectionReasons()).containsExactly("irrelevant");
            });
    }

    @Test
    void rejectionCapsSuggestedPrompts() {
        List<String> prompts = java.util.stream.IntStream.range(0, 7)
            .mapToObj(i -> "prompt " + i)
            .toList();
        QueryAnalysis analysis = rejected(List.of("irrelevant"), prompts);

        assertThatThrownBy(() -> validator.validate(analysis))
            .isInstanceOf(GuardrailRejectedException.class)
            .satisfies(ex -> assertThat(((GuardrailRejectedException) ex).getQueryAnalysis().suggestedPrompts())
                .containsExactlyElementsOf(prompts.subList(0, QueryAnalysisValidator.MAX_SUGGESTED_PROMPTS)));
    }

    @Test
    void rejectionWithOnlyInvalidSuggestionsGetsGenericPrompt() {
        QueryAnalysis analysis = rejected(List.of("irrelevant"), List.of("bad\u0007prompt"));

        assertThatThrownBy(() -> validator.validate(analysis))
            .isInstanceOf(GuardrailRejectedException.class)
            .satisfies(ex -> assertThat(((GuardrailRejectedException) ex).getQueryAnalysis().suggestedPrompts())
                .containsExactly(QueryAnalysisValidator.GENERIC_SUGGESTED_PROMPT));
    }

    @Test
    void nullAnalysisThrowsIllegalAssistantResponse() {
        assertThatThrownBy(() -> validator.validate(null))
            .isInstanceOf(IllegalAssistantResponseException.class);
    }

    @Test
    void blankSearchQueryThrowsIllegalAssistantResponse() {
        QueryAnalysis analysis = allowed("", List.of(), null, false);

        assertThatThrownBy(() -> validator.validate(analysis))
            .isInstanceOf(IllegalAssistantResponseException.class);
    }

    @Test
    void nullSearchQueryThrowsIllegalAssistantResponse() {
        QueryAnalysis analysis = allowed(null, List.of(), null, false);

        assertThatThrownBy(() -> validator.validate(analysis))
            .isInstanceOf(IllegalAssistantResponseException.class);
    }

    @Test
    void overLongSearchQueryThrowsIllegalAssistantResponse() {
        QueryAnalysis analysis = allowed("a".repeat(501), List.of(), null, false);

        assertThatThrownBy(() -> validator.validate(analysis))
            .isInstanceOf(IllegalAssistantResponseException.class);
    }

    @Test
    void nonPlainTextSearchQueryThrowsIllegalAssistantResponse() {
        QueryAnalysis analysis = allowed("jacket\u0007bad", List.of(), null, false);

        assertThatThrownBy(() -> validator.validate(analysis))
            .isInstanceOf(IllegalAssistantResponseException.class);
    }

    @Test
    void negativePriceThrowsIllegalAssistantResponse() {
        QueryAnalysis analysis = allowed("jacket", List.of(),
                new PriceConstraint(PriceOperator.LESS_THAN, new BigDecimal("-5")), false);

        assertThatThrownBy(() -> validator.validate(analysis))
            .isInstanceOf(IllegalAssistantResponseException.class);
    }

    @Test
    void zeroPriceThrowsIllegalAssistantResponse() {
        QueryAnalysis analysis = allowed("jacket", List.of(),
                new PriceConstraint(PriceOperator.LESS_THAN_OR_EQUAL, BigDecimal.ZERO), false);

        assertThatThrownBy(() -> validator.validate(analysis))
            .isInstanceOf(IllegalAssistantResponseException.class);
    }

    @Test
    void tooManySuggestedPromptsThrowsIllegalAssistantResponse() {
        List<String> prompts = java.util.stream.IntStream.range(0, 6)
            .mapToObj(i -> "prompt " + i)
            .toList();
        QueryAnalysis analysis = allowed("jacket", List.of(), null, false, prompts);

        assertThatThrownBy(() -> validator.validate(analysis))
            .isInstanceOf(IllegalAssistantResponseException.class);
    }

    @Test
    void tooManyNegativeExamplesThrowsIllegalAssistantResponse() {
        List<String> negatives = java.util.stream.IntStream.range(0, 11)
            .mapToObj(i -> "negative " + i)
            .toList();
        QueryAnalysis analysis = allowed("jacket", negatives, null, false);

        assertThatThrownBy(() -> validator.validate(analysis))
            .isInstanceOf(IllegalAssistantResponseException.class);
    }

    @Test
    void overLongSuggestedPromptThrowsIllegalAssistantResponse() {
        QueryAnalysis analysis = allowed("jacket", List.of(), null, false,
                List.of("a".repeat(301)));

        assertThatThrownBy(() -> validator.validate(analysis))
            .isInstanceOf(IllegalAssistantResponseException.class);
    }

    private static QueryAnalysis allowed(String searchQuery, List<String> negativeExamples,
            PriceConstraint priceConstraint, boolean sortByLowestPrice) {
        return new QueryAnalysis(true, List.of(), List.of(), searchQuery, negativeExamples,
                priceConstraint, sortByLowestPrice);
    }

    private static QueryAnalysis allowed(String searchQuery, List<String> negativeExamples,
            PriceConstraint priceConstraint, boolean sortByLowestPrice, List<String> suggestedPrompts) {
        return new QueryAnalysis(true, List.of(), suggestedPrompts, searchQuery, negativeExamples,
                priceConstraint, sortByLowestPrice);
    }

    private static QueryAnalysis rejected(List<String> reasons, List<String> suggestedPrompts) {
        return new QueryAnalysis(false, reasons, suggestedPrompts, "", List.of(), null, false);
    }
}
