package com.ecomassistant.guardrails;

import com.ecomassistant.retrieval.RetrievalContextKeys;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.filter.Filter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalParametersMapperTest {

    private final RetrievalParametersMapper mapper = new RetrievalParametersMapper();

    @Test
    void mapsLessThanPriceConstraint() {
        Map<String, Object> context = mapper.toContext(analysis(
                new PriceConstraint(PriceOperator.LESS_THAN, new BigDecimal("150"))));

        Filter.Expression filter = (Filter.Expression) context.get(RetrievalContextKeys.FILTER_EXPRESSION);
        assertThat(filter.type()).isEqualTo(Filter.ExpressionType.LT);
        assertThat(((Filter.Key) filter.left()).key()).isEqualTo("price");
        assertThat((BigDecimal) ((Filter.Value) filter.right()).value()).isEqualByComparingTo("150");
    }

    @Test
    void mapsLessThanOrEqualPriceConstraint() {
        Map<String, Object> context = mapper.toContext(analysis(
                new PriceConstraint(PriceOperator.LESS_THAN_OR_EQUAL, new BigDecimal("150"))));

        Filter.Expression filter = (Filter.Expression) context.get(RetrievalContextKeys.FILTER_EXPRESSION);
        assertThat(filter.type()).isEqualTo(Filter.ExpressionType.LTE);
        assertThat(((Filter.Key) filter.left()).key()).isEqualTo("price");
        assertThat((BigDecimal) ((Filter.Value) filter.right()).value()).isEqualByComparingTo("150");
    }

    @Test
    void mapsGreaterThanPriceConstraint() {
        Map<String, Object> context = mapper.toContext(analysis(
                new PriceConstraint(PriceOperator.GREATER_THAN, new BigDecimal("100"))));

        Filter.Expression filter = (Filter.Expression) context.get(RetrievalContextKeys.FILTER_EXPRESSION);
        assertThat(filter.type()).isEqualTo(Filter.ExpressionType.GT);
    }

    @Test
    void mapsGreaterThanOrEqualPriceConstraint() {
        Map<String, Object> context = mapper.toContext(analysis(
                new PriceConstraint(PriceOperator.GREATER_THAN_OR_EQUAL, new BigDecimal("100"))));

        Filter.Expression filter = (Filter.Expression) context.get(RetrievalContextKeys.FILTER_EXPRESSION);
        assertThat(filter.type()).isEqualTo(Filter.ExpressionType.GTE);
    }

    @Test
    void mapsSortByLowestPrice() {
        Map<String, Object> context = mapper.toContext(new QueryAnalysis(
                true, List.of(), List.of(), "jacket", List.of(), null, true));

        assertThat(context.get(RetrievalContextKeys.SORT_BY_LOWEST_PRICE)).isEqualTo(Boolean.TRUE);
    }

    @Test
    void mapsNegativeExamples() {
        Map<String, Object> context = mapper.toContext(new QueryAnalysis(
                true, List.of(), List.of(), "jacket", List.of("wool", "leather"), null, false));

        assertThat(context.get(RetrievalContextKeys.NEGATIVE_EXAMPLES))
            .isEqualTo(List.of("wool", "leather"));
    }

    @Test
    void omitsAllKeysWhenNoParametersProvided() {
        Map<String, Object> context = mapper.toContext(new QueryAnalysis(
                true, List.of(), List.of(), "jacket", List.of(), null, false));

        assertThat(context).isEmpty();
    }

    private static QueryAnalysis analysis(PriceConstraint priceConstraint) {
        return new QueryAnalysis(true, List.of(), List.of(), "jacket", List.of(), priceConstraint, false);
    }
}
