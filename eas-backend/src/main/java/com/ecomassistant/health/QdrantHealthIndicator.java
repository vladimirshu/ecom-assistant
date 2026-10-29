package com.ecomassistant.health;

import io.qdrant.client.QdrantClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Reports the vector store as reachable only when the configured Qdrant collection can be
 * confirmed within a short timeout. It calls only {@code collectionExistsAsync} and never
 * touches the embedding model or any LLM, so the readiness probe stays free to poll.
 */
@Component
public class QdrantHealthIndicator extends AbstractHealthIndicator {

    private final QdrantClient qdrantClient;
    private final String collectionName;

    public QdrantHealthIndicator(QdrantClient qdrantClient,
            @Value("${spring.ai.vectorstore.qdrant.collection-name:products}") String collectionName) {
        this.qdrantClient = qdrantClient;
        this.collectionName = collectionName;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            if (qdrantClient.collectionExistsAsync(collectionName).get(1, TimeUnit.SECONDS)) {
                builder.up();
            } else {
                builder.down();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            builder.down();
        } catch (Exception e) {
            builder.down();
        }
    }
}
