package com.ecomassistant.service;

import com.ecomassistant.VectorStoreUtils;
import com.ecomassistant.entity.Product;
import com.ecomassistant.retrieval.QdrantRecommendDocumentRetriever;
import com.ecomassistant.retrieval.RetrievalContextKeys;
import com.ecomassistant.tools.AssistantToolsCallback;
import com.ecomassistant.tools.PriceOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);

    private final QdrantRecommendDocumentRetriever retriever;

    public ProductSearchService(QdrantRecommendDocumentRetriever retriever) {
        this.retriever = retriever;
    }

    /**
     * Search products by building a retrieval {@link Query} from the values collected on
     * the {@link AssistantToolsCallback} and delegating to the retriever.
     */
    public List<Product> searchProducts(String query, AssistantToolsCallback callback) {
        log.info("Searching products with query: '{}', negatives: {}, filterByPrice: {}, sortByPriceAsc: {}",
                query, callback.getNegativeExamples(), callback.isFilterByPrice(), callback.isSortByPriceAsc());

        List<Document> foundDocuments = retriever.retrieve(buildQuery(query, callback));
        List<Product> foundProducts = foundDocuments.stream().map(VectorStoreUtils::documentToProduct).toList();

        log.info("Found {} products matching the search criteria", foundProducts.size());
        return foundProducts;
    }

    private Query buildQuery(String query, AssistantToolsCallback callback) {
        Map<String, Object> context = new HashMap<>();

        if (callback.isFilterByPrice()) {
            context.put(RetrievalContextKeys.FILTER_EXPRESSION, buildPriceFilterExpression(callback));
        }
        if (callback.isSortByPriceAsc()) {
            context.put(RetrievalContextKeys.SORT_BY_LOWEST_PRICE, Boolean.TRUE);
        }
        if (!callback.getNegativeExamples().isEmpty()) {
            context.put(RetrievalContextKeys.NEGATIVE_EXAMPLES, callback.getNegativeExamples());
        }

        return Query.builder().text(query).context(context).build();
    }

    private Filter.Expression buildPriceFilterExpression(AssistantToolsCallback callback) {
        BigDecimal price = new BigDecimal(callback.getFilterProductsByPriceValue());
        PriceOperator operator = callback.getFilterProductsByPriceOperator();
        FilterExpressionBuilder builder = new FilterExpressionBuilder();

        return switch (operator) {
            case LESS_THAN -> builder.lt("price", price.doubleValue()).build();
            case GREATER_THAN -> builder.gt("price", price.doubleValue()).build();
            case LESS_THAN_OR_EQUAL -> builder.lte("price", price.doubleValue()).build();
            case GREATER_THAN_OR_EQUAL -> builder.gte("price", price.doubleValue()).build();
            case LOWEST, NOT_RELEVANT ->
                throw new IllegalArgumentException("Unsupported price operator: " + operator);
        };
    }
}
