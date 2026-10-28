package com.ecomassistant.dto;

import com.ecomassistant.guardrails.PriceConstraint;
import com.ecomassistant.guardrails.PriceOperator;
import com.ecomassistant.guardrails.QueryAnalysis;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchInterpretationDtoTest {

    @Test
    void mapsFullAnalysis() {
        QueryAnalysis analysis = new QueryAnalysis(true, List.of(), List.of(),
                "blue cotton jacket", List.of("wool"),
                new PriceConstraint(PriceOperator.LESS_THAN, new BigDecimal("200")), false);

        SearchInterpretationDto dto = SearchInterpretationDto.from(analysis);

        assertThat(dto.searchQuery()).isEqualTo("blue cotton jacket");
        assertThat(dto.negativeExamples()).containsExactly("wool");
        assertThat(dto.priceConstraint().operator()).isEqualTo(PriceOperator.LESS_THAN);
        assertThat(dto.priceConstraint().value()).isEqualByComparingTo(new BigDecimal("200"));
        assertThat(dto.sortByLowestPrice()).isFalse();
    }

    @Test
    void mapsAnalysisWithoutConstraints() {
        QueryAnalysis analysis = new QueryAnalysis(true, List.of(), List.of(),
                "rain jacket", List.of(), null, false);

        SearchInterpretationDto dto = SearchInterpretationDto.from(analysis);

        assertThat(dto.priceConstraint()).isNull();
        assertThat(dto.negativeExamples()).isEmpty();
    }

    @Test
    void nullNegativeExamplesMapToEmptyList() {
        QueryAnalysis analysis = new QueryAnalysis(true, List.of(), List.of(),
                "rain jacket", null, null, false);

        SearchInterpretationDto dto = SearchInterpretationDto.from(analysis);

        assertThat(dto.negativeExamples()).isEmpty();
    }

    @Test
    void mapsSortByLowestPrice() {
        QueryAnalysis analysis = new QueryAnalysis(true, List.of(), List.of(),
                "puffer jacket", List.of(), null, true);

        assertThat(SearchInterpretationDto.from(analysis).sortByLowestPrice()).isTrue();
    }

    @Test
    void nullAnalysisMapsToNull() {
        assertThat(SearchInterpretationDto.from(null)).isNull();
    }
}
