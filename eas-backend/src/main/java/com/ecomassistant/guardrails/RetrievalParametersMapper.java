package com.ecomassistant.guardrails;

import com.ecomassistant.retrieval.RetrievalContextKeys;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps an allowed {@link QueryAnalysis} to the retrieval context keys consumed by
 * {@link com.ecomassistant.retrieval.QdrantRecommendDocumentRetriever}.
 *
 * <p>Each key is added only when the analysis actually provides the corresponding value,
 * so a request without price, negatives or ordering results in an empty context.
 */
@Component
public class RetrievalParametersMapper {

    private static final String PRICE_KEY = "price";

    public Map<String, Object> toContext(QueryAnalysis analysis) {
        Map<String, Object> context = new HashMap<>();

        if (analysis.priceConstraint() != null) {
            context.put(RetrievalContextKeys.FILTER_EXPRESSION, buildPriceFilter(analysis.priceConstraint()));
        }
        if (analysis.negativeExamples() != null && !analysis.negativeExamples().isEmpty()) {
            context.put(RetrievalContextKeys.NEGATIVE_EXAMPLES, analysis.negativeExamples());
        }
        if (analysis.sortByLowestPrice()) {
            context.put(RetrievalContextKeys.SORT_BY_LOWEST_PRICE, Boolean.TRUE);
        }

        return context;
    }

    private Filter.Expression buildPriceFilter(PriceConstraint constraint) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        BigDecimal value = constraint.value();
        return switch (constraint.operator()) {
            case LESS_THAN -> builder.lt(PRICE_KEY, value).build();
            case LESS_THAN_OR_EQUAL -> builder.lte(PRICE_KEY, value).build();
            case GREATER_THAN -> builder.gt(PRICE_KEY, value).build();
            case GREATER_THAN_OR_EQUAL -> builder.gte(PRICE_KEY, value).build();
        };
    }
}
