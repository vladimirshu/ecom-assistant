package com.ecomassistant.retrieval;

import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;

/**
 * Context keys read by {@link QdrantRecommendDocumentRetriever} from a
 * {@link org.springframework.ai.rag.Query}'s context map.
 */
public final class RetrievalContextKeys {

    /**
     * Reused standard Spring AI key so the retriever stays interchangeable with
     * {@link VectorStoreDocumentRetriever}. Value may be a
     * {@link org.springframework.ai.vectorstore.filter.Filter.Expression} or its string form.
     */
    public static final String FILTER_EXPRESSION = VectorStoreDocumentRetriever.FILTER_EXPRESSION;

    /** Value: {@code List<String>}. */
    public static final String NEGATIVE_EXAMPLES = "ecom_negative_examples";

    /** Value: {@code Boolean}. */
    public static final String SORT_BY_LOWEST_PRICE = "ecom_sort_by_lowest_price";

    private RetrievalContextKeys() {
    }
}
