package com.ecomassistant.service;

import com.ecomassistant.VectorStoreUtils;
import com.ecomassistant.converter.QdrantFilterExpressionConverter;
import com.ecomassistant.entity.Product;
import com.nimbusds.oauth2.sdk.util.CollectionUtils;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.VectorInputFactory;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.grpc.Points;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static io.qdrant.client.QueryFactory.recommend;
import static io.qdrant.client.VectorInputFactory.vectorInput;
import static io.qdrant.client.VectorsFactory.vectors;

@Service
public class ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);
    public static final int TOP_K = 10;
    public static final float SIMILARITY_THRESHOLD = 0.25f;
    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final QdrantFilterExpressionConverter filterExpressionConverter = new QdrantFilterExpressionConverter();
    
    @Value("${spring.ai.vectorstore.qdrant.collection-name:products}")
    private String collectionName;

    public ProductSearchService(VectorStore vectorStore, EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }

    /**
     * Search products using similarity search with optional sorting
     *
     * @param query       The search query text
     * @param products    Optional list of predefined products to search within (null for all products)
     * @param sortByPrice Optional sort parameter: "asc", "desc", or null for no sorting
     * @return List of matching products
     */
    public List<Product> searchProducts(String query, List<String> negativeExamples, List<Product> products,
                                        String sortByPrice) {
        log.info(
                "Searching products with query: '{}', having negativeExamples: {}, predefined products count: {}, sort: {}",
                query, negativeExamples, products != null ? products.size() : "all", sortByPrice);

        // execute similarity search
        List<Document> foundDocuments = doSearch(query, negativeExamples, products);

        // Convert documents to products
        List<Product> foundProducts = foundDocuments.stream().map(VectorStoreUtils::documentToProduct).collect(Collectors.toList());

        // Apply sorting if requested
        if (sortByPrice != null) {
            foundProducts = sortProducts(foundProducts, sortByPrice);
        }

        log.info("Found {} products matching the search criteria", foundProducts.size());
        return foundProducts;
    }

    private List<Document> doSearch(String query, List<String> negativeExamples, List<Product> products) {
        QdrantClient qdrantClient = (QdrantClient) vectorStore.getNativeClient().get();
        Points.RecommendInput.Builder recommendInputBuilder = getRecommendInputBuilder(query, negativeExamples);
        Points.QueryPoints.Builder queryPointsBuilder = Points.QueryPoints.newBuilder();
        queryPointsBuilder
                .setQuery(recommend(recommendInputBuilder.build()))
                .setWithPayload(WithPayloadSelectorFactory.enable(true))
                .setScoreThreshold(SIMILARITY_THRESHOLD)
                .setLimit(TOP_K)
                .setCollectionName(collectionName);

        // If specific products are provided, create a filter to search only among them
        if (CollectionUtils.isNotEmpty(products)) {
            addProductsFilterToPointsBuilder(products, queryPointsBuilder);
        }

        try {
            List<Points.ScoredPoint> scoredPoints = qdrantClient.queryAsync(queryPointsBuilder.build()).get();
            return scoredPoints.stream().map(VectorStoreUtils::qdrantPointToDocument).toList();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private void addProductsFilterToPointsBuilder(List<Product> products, Points.QueryPoints.Builder queryPointsBuilder) {
        List<String> skus = products.stream().map(Product::getSku).toList();
        Filter.Expression filterExpression = new FilterExpressionBuilder().in("sku", skus.toArray()).build();
        Points.Filter filter = filterExpressionConverter.convertExpression(filterExpression);
        queryPointsBuilder.setFilter(filter);
    }

    private Points.RecommendInput.Builder getRecommendInputBuilder(String query, List<String> negativeExamples) {
        float[] queryEmbedding = embeddingModel.embed(query);
        Points.RecommendInput.Builder recommendInputBuilder = Points.RecommendInput.newBuilder()
                .addAllPositive(List.of(vectorInput(queryEmbedding)))
                .setStrategy(Points.RecommendStrategy.BestScore);
        if (CollectionUtils.isNotEmpty(negativeExamples)) {
            recommendInputBuilder.addAllNegative(negativeExamples.stream()
                    .map(embeddingModel::embed).map(VectorInputFactory::vectorInput).toList());
        }
        return recommendInputBuilder;
    }

    /**
     * Sort products by price
     */
    private List<Product> sortProducts(List<Product> products, String sortByPrice) {
        if ("asc".equalsIgnoreCase(sortByPrice)) {
            return products.stream()
                    .sorted(Comparator.comparing(Product::getPrice, Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());
        }
        return products;
    }
}