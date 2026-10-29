package com.ecomassistant.retrieval;

import io.qdrant.client.QdrantClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

@SpringBootTest
@Testcontainers
public abstract class AbstractRetrievalIntegrationTest {

    @Container
    @ServiceConnection
    static final QdrantContainer qdrant = new QdrantContainer("qdrant/qdrant:v1.13.4");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // Disambiguate auto-configuration because spring-ai-spring-boot-testcontainers
        // brings several vector-store and model auto-configurations onto the classpath.
        registry.add("spring.ai.vectorstore.type", () -> "qdrant");
        registry.add("spring.ai.model.chat", () -> "openai");
        registry.add("spring.ai.model.embedding", () -> "none");
        registry.add("spring.ai.openai.api-key", () -> "test-key");
        registry.add("ecom.import.sample-file", () -> "sampledata/test-apparel.csv");
        // The rate limiter and daily budget are exercised by their own tests; keep them
        // effectively unlimited so unrelated integration tests never hit a 429.
        registry.add("ecom.limits.per-minute", () -> "1000000");
        registry.add("ecom.limits.per-hour", () -> "1000000");
        registry.add("ecom.limits.daily-budget", () -> "1000000");
    }

    @Autowired
    protected QdrantClient qdrantClient;

    @Autowired
    protected EmbeddingModel embeddingModel;

    @Autowired
    protected PricePayloadIndexInitializer pricePayloadIndexInitializer;

    protected QdrantRecommendDocumentRetriever retriever(int topK, float threshold) {
        return new QdrantRecommendDocumentRetriever(qdrantClient, embeddingModel, "products", topK, threshold);
    }
}
