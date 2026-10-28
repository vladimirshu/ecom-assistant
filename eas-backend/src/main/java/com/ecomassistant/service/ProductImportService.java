package com.ecomassistant.service;

import com.ecomassistant.VectorStoreUtils;
import com.ecomassistant.catalog.Product;
import com.ecomassistant.catalog.ProductCategory;
import com.ecomassistant.config.EcomImportProperties;
import io.qdrant.client.ConditionFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Common;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductImportService {

    private final VectorStore vectorStore;
    private final EcomImportProperties importProperties;
    private final QdrantClient qdrantClient;

    /** Maximum accepted title length. */
    private static final int MAX_TITLE_LENGTH = 80;

    @Value("${spring.ai.vectorstore.qdrant.collection-name:products}")
    private String collectionName;

    /**
     * Idempotent upsert import with Qdrant as the only store. Runs when the collection is
     * empty or missing, or when any stored point lacks a category (the upgrade path from
     * the previous schema). Points have deterministic IDs derived from the SKU, so
     * re-running the import does not create duplicates. The check uses point counts only
     * and never calls the embedding model.
     */
    public void importSampleProducts() {
        long total = countPoints(null);
        long withoutCategory = total == 0 ? 0 : countPoints(categoryMissingFilter());

        if (total > 0 && withoutCategory == 0) {
            log.info("Products already imported. Skipping");
            return;
        }

        List<Product> products = extractProducts();
        if (products.isEmpty()) {
            return;
        }

        // Delete stale points from the previous random-ID import (those without a
        // category payload) before upserting the deterministic points.
        if (withoutCategory > 0) {
            deletePointsWithoutCategory();
        }

        // Upsert into Qdrant: point IDs are deterministic (UUID.nameUUIDFromBytes(sku)).
        vectorStore.add(products.stream().map(VectorStoreUtils::productToDocument).toList());

        log.info("Successfully imported {} products from CSV file", products.size());
    }

    private List<Product> extractProducts() {
        ClassPathResource resource = new ClassPathResource(importProperties.sampleFile());
        List<Product> products = new ArrayList<>();

        try (Reader reader = new InputStreamReader(
                resource.getInputStream()); CSVParser csvParser = CSVFormat.Builder.create().setHeader()
                .setSkipHeaderRecord(true).setDelimiter(";").build().parse(reader)) {

            for (CSVRecord csvRecord : csvParser) {
                Product product = parseProductFromRecord(csvRecord);
                if (product != null) {
                    products.add(product);
                }
            }
        } catch (IOException e) {
            log.error("Error reading CSV file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to import products from CSV", e);
        } catch (Exception e) {
            log.error("Error importing products: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to import products", e);
        }
        return products;
    }

    /** Exact point count for the filter, or 0 when the collection does not exist. */
    private long countPoints(Common.Filter filter) {
        try {
            if (!qdrantClient.collectionExistsAsync(collectionName).get()) {
                return 0;
            }
            return qdrantClient.countAsync(collectionName, filter, true).get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while counting Qdrant points", e);
        }
        catch (ExecutionException e) {
            throw new IllegalStateException("Failed to count Qdrant points", e);
        }
    }

    // `isEmpty` matches a missing, null or empty value, unlike `isNull` which only
    // matches a field that exists with a NULL value.
    private static Common.Filter categoryMissingFilter() {
        return Common.Filter.newBuilder()
            .addMust(ConditionFactory.isEmpty("category"))
            .build();
    }

    /**
     * Deletes points that carry no {@code category} payload. These are leftovers from the
     * previous import, which used random UUID point IDs and did not store a category;
     * they would otherwise remain in the collection as duplicates of the newly upserted
     * deterministic points.
     */
    private void deletePointsWithoutCategory() {
        try {
            if (!qdrantClient.collectionExistsAsync(collectionName).get()) {
                return;
            }
            qdrantClient.deleteAsync(collectionName, categoryMissingFilter()).get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while deleting stale Qdrant points", e);
        }
        catch (ExecutionException e) {
            throw new IllegalStateException("Failed to delete stale Qdrant points", e);
        }
    }

    Product parseProductFromRecord(CSVRecord record) {
        String sku = field(record, "sku");
        String title = field(record, "title");
        String categorySlug = field(record, "category");
        String colors = field(record, "colors");
        String sizes = field(record, "sizes");
        String priceStr = field(record, "price");
        String image = field(record, "image");
        String description = field(record, "description");

        if (isBlank(sku) || isBlank(title) || isBlank(categorySlug) || isBlank(colors)
                || isBlank(sizes) || isBlank(priceStr) || isBlank(description)) {
            log.warn("Skipping CSV row with SKU {}: a required field is missing", sku);
            return null;
        }

        if (title.length() > MAX_TITLE_LENGTH) {
            log.warn("Skipping CSV row with SKU {}: title longer than {} characters", sku, MAX_TITLE_LENGTH);
            return null;
        }

        ProductCategory category = ProductCategory.fromSlug(categorySlug);
        if (category == null) {
            log.warn("Skipping CSV row with SKU {}: unknown category '{}'", sku, categorySlug);
            return null;
        }

        BigDecimal price;
        try {
            price = new BigDecimal(priceStr);
        }
        catch (NumberFormatException e) {
            log.warn("Skipping CSV row with SKU {}: unparsable price '{}'", sku, priceStr);
            return null;
        }
        if (price.signum() <= 0) {
            log.warn("Skipping CSV row with SKU {}: price must be greater than 0, got '{}'", sku, priceStr);
            return null;
        }

        String imageKey = isBlank(image) ? "categories/" + category.slug() + ".webp" : image;

        Product product = new Product();
        product.setSku(sku);
        product.setTitle(title);
        product.setCategory(category);
        product.setImageKey(imageKey);
        product.setColors(colors);
        product.setSizes(sizes);
        product.setPrice(price);
        product.setDescription(description);
        return product;
    }

    /**
     * Returns the trimmed value of a column, or {@code null} when the row is too short to
     * contain it, so a malformed row is skipped instead of aborting the whole import.
     */
    private static String field(CSVRecord record, String name) {
        return record.isSet(name) ? record.get(name).trim() : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
