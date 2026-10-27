package com.ecomassistant.retrieval;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QdrantRecommendDocumentRetrieverIntegrationTest extends AbstractRetrievalIntegrationTest {

    @Test
    void similaritySearchReturnsRelevantProductsMostRelevantFirst() {
        QdrantRecommendDocumentRetriever r = retriever(3, 0.0f);

        List<Document> results = r.retrieve(query("waterproof nylon rain jacket", Map.of()));

        assertThat(skus(results)).containsExactlyInAnyOrder("RAIN1", "RAIN2", "RAIN3");
        assertThat(results).hasSize(3);
        for (int i = 1; i < results.size(); i++) {
            assertThat(results.get(i - 1).getScore()).isGreaterThanOrEqualTo(results.get(i).getScore());
        }
    }

    @Test
    void returnsEmptyWhenNothingAboveThreshold() {
        QdrantRecommendDocumentRetriever r = retriever(10, 0.99f);

        List<Document> results = r.retrieve(query("waterproof nylon rain jacket", Map.of()));

        assertThat(results).isEmpty();
    }

    @Test
    void negativeExamplesPushWoolProductsDown() {
        QdrantRecommendDocumentRetriever r = retriever(10, 0.0f);

        List<String> without = skus(r.retrieve(query("warm jacket", Map.of())));
        List<String> with = skus(r.retrieve(query("warm jacket",
                Map.of(RetrievalContextKeys.NEGATIVE_EXAMPLES, List.of("wool")))));

        // The strongly-wool product is present without negatives and demoted below the
        // similarity threshold when "wool" is a negative example.
        assertThat(without).contains("WOOL1");
        assertThat(with).doesNotContain("WOOL1");
    }

    @Test
    void emptyNegativesEqualsNoNegativesKey() {
        QdrantRecommendDocumentRetriever r = retriever(10, 0.0f);

        List<String> without = skus(r.retrieve(query("warm winter jacket", Map.of())));
        List<String> empty = skus(r.retrieve(query("warm winter jacket",
                Map.of(RetrievalContextKeys.NEGATIVE_EXAMPLES, List.of()))));

        assertThat(empty).containsExactlyElementsOf(without);
    }

    @Test
    void filterExpressionUpperBoundOnPrice() {
        QdrantRecommendDocumentRetriever r = retriever(10, 0.0f);
        Filter.Expression filter = new FilterExpressionBuilder().lte("price", 200.0).build();

        List<Document> results = r.retrieve(query("jacket",
                Map.of(RetrievalContextKeys.FILTER_EXPRESSION, filter)));

        assertThat(skus(results)).containsExactlyInAnyOrder("RAIN1", "RAIN2", "WOOL2", "DENIM1");
        assertThat(prices(results)).allMatch(price -> price.compareTo(new BigDecimal("200")) <= 0);
    }

    @Test
    void filterExpressionAsStringOnPrice() {
        QdrantRecommendDocumentRetriever r = retriever(10, 0.0f);

        List<Document> results = r.retrieve(query("jacket",
                Map.of(RetrievalContextKeys.FILTER_EXPRESSION, "price > 250")));

        assertThat(skus(results)).containsExactly("RAIN3");
        assertThat(prices(results)).allMatch(price -> price.compareTo(new BigDecimal("250")) > 0);
    }

    @Test
    void invalidFilterValueTypeFailsFast() {
        QdrantRecommendDocumentRetriever r = retriever(10, 0.0f);

        assertThatThrownBy(() -> r.retrieve(query("jacket",
                Map.of(RetrievalContextKeys.FILTER_EXPRESSION, 123))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lowestPriceOrderingReturnsSameSetOrderedAscending() {
        QdrantRecommendDocumentRetriever r = retriever(10, 0.0f);

        List<String> unsorted = skus(r.retrieve(query("rain jacket", Map.of())));
        List<Document> sorted = r.retrieve(query("rain jacket",
                Map.of(RetrievalContextKeys.SORT_BY_LOWEST_PRICE, true)));

        assertThat(skus(sorted)).containsExactlyInAnyOrderElementsOf(unsorted);
        assertThat(prices(sorted)).isSorted();
    }

    @Test
    void orderingCombinedWithFilterAndNegatives() {
        QdrantRecommendDocumentRetriever r = retriever(10, 0.0f);
        Filter.Expression filter = new FilterExpressionBuilder().lte("price", 300.0).build();

        List<Document> results = r.retrieve(query("rain jacket", Map.of(
                RetrievalContextKeys.FILTER_EXPRESSION, filter,
                RetrievalContextKeys.NEGATIVE_EXAMPLES, List.of("wool"),
                RetrievalContextKeys.SORT_BY_LOWEST_PRICE, true)));

        assertThat(prices(results)).isSorted();
        assertThat(prices(results)).allMatch(price -> price.compareTo(new BigDecimal("300")) <= 0);
    }

    @Test
    void priceIndexCreationIsIdempotent() {
        assertThatCode(pricePayloadIndexInitializer::ensurePriceIndex).doesNotThrowAnyException();
    }

    private static Query query(String text, Map<String, Object> context) {
        return Query.builder().text(text).context(context).build();
    }

    private static List<String> skus(List<Document> documents) {
        return documents.stream().map(document -> (String) document.getMetadata().get("sku")).toList();
    }

    private static List<BigDecimal> prices(List<Document> documents) {
        return documents.stream()
            .map(document -> new BigDecimal(document.getMetadata().get("price").toString()))
            .toList();
    }
}
