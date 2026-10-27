package com.ecomassistant.retrieval;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

/**
 * Ensures a float payload index exists on {@code price} at application startup. The index
 * is required for efficient range filtering and for {@code order_by price}. Creating an
 * already existing index is treated as success (idempotent).
 */
@Component
public class PricePayloadIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PricePayloadIndexInitializer.class);

    private final QdrantClient qdrantClient;
    private final String collectionName;

    public PricePayloadIndexInitializer(QdrantClient qdrantClient,
            @Value("${spring.ai.vectorstore.qdrant.collection-name:products}") String collectionName) {
        this.qdrantClient = qdrantClient;
        this.collectionName = collectionName;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensurePriceIndex();
    }

    public void ensurePriceIndex() {
        try {
            if (!qdrantClient.collectionExistsAsync(collectionName).get()) {
                log.debug("Collection '{}' does not exist yet; skipping price index creation", collectionName);
                return;
            }
            if (qdrantClient.getCollectionInfoAsync(collectionName).get()
                .getPayloadSchemaMap()
                .containsKey("price")) {
                log.debug("Payload index on 'price' already exists in collection '{}'", collectionName);
                return;
            }
            qdrantClient.createPayloadIndexAsync(collectionName, "price", Collections.PayloadSchemaType.Float,
                    null, true, null, null)
                .get();
            log.info("Created float payload index on 'price' in collection '{}'", collectionName);
        }
        catch (ExecutionException e) {
            if (isAlreadyExists(e)) {
                log.debug("Payload index on 'price' already exists in collection '{}'", collectionName);
                return;
            }
            throw new IllegalStateException("Failed to create payload index on 'price'", e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating payload index on 'price'", e);
        }
    }

    private static boolean isAlreadyExists(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null
                    && (message.contains("already exists") || message.contains("Already exists")
                            || message.contains("already_exists"))) {
                return true;
            }
        }
        return false;
    }
}
