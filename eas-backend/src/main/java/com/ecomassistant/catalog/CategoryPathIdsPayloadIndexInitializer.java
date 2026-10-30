package com.ecomassistant.catalog;

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
 * Ensures a keyword payload index exists on {@code categoryPathIds} at application startup,
 * required for the indexed membership match used by catalog listing. Creating an already
 * existing index is treated as success (idempotent).
 */
@Component
public class CategoryPathIdsPayloadIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CategoryPathIdsPayloadIndexInitializer.class);

    private final QdrantClient qdrantClient;
    private final String collectionName;

    public CategoryPathIdsPayloadIndexInitializer(QdrantClient qdrantClient,
            @Value("${spring.ai.vectorstore.qdrant.collection-name:products}") String collectionName) {
        this.qdrantClient = qdrantClient;
        this.collectionName = collectionName;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureCategoryPathIdsIndex();
    }

    public void ensureCategoryPathIdsIndex() {
        try {
            if (!qdrantClient.collectionExistsAsync(collectionName).get()) {
                log.debug("Collection '{}' does not exist yet; skipping categoryPathIds index creation",
                        collectionName);
                return;
            }
            if (qdrantClient.getCollectionInfoAsync(collectionName).get()
                .getPayloadSchemaMap()
                .containsKey("categoryPathIds")) {
                log.debug("Payload index on 'categoryPathIds' already exists in collection '{}'",
                        collectionName);
                return;
            }
            qdrantClient.createPayloadIndexAsync(collectionName, "categoryPathIds",
                    Collections.PayloadSchemaType.Keyword, null, true, null, null).get();
            log.info("Created keyword payload index on 'categoryPathIds' in collection '{}'",
                    collectionName);
        }
        catch (ExecutionException e) {
            if (isAlreadyExists(e)) {
                log.debug("Payload index on 'categoryPathIds' already exists in collection '{}'",
                        collectionName);
                return;
            }
            throw new IllegalStateException("Failed to create payload index on 'categoryPathIds'", e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating payload index on 'categoryPathIds'", e);
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
