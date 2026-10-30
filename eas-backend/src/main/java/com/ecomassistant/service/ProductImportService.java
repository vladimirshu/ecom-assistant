package com.ecomassistant.service;

import com.ecomassistant.VectorStoreUtils;
import com.ecomassistant.catalog.CatalogCategoryRegistry;
import com.ecomassistant.catalog.Product;
import com.ecomassistant.catalog.ProductCategory;
import com.ecomassistant.config.EcomImportProperties;
import com.ecomassistant.listing.CatalogReadiness;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.Points;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Revision-aware, retry-safe catalog import into Qdrant (the only persistent store).
 *
 * <p>Each import computes a desired catalog revision from the authored CSV and the
 * browsing category id/edge configuration, then upserts the desired manifest by
 * deterministic SKU-derived ids, verifies every expected SKU and payload before removing
 * obsolete or legacy points, and only then marks the catalog verified/ready. A completed
 * current revision is skipped without any embedding call; a partial or failed import
 * throws so startup/readiness fails and the next startup retries.
 */
@Service
@Slf4j
public class ProductImportService {

    private final VectorStore vectorStore;
    private final EcomImportProperties importProperties;
    private final QdrantClient qdrantClient;
    private final CatalogCategoryRegistry categoryRegistry;
    private final CatalogReadiness catalogReadiness;

    /** Maximum accepted title length. */
    private static final int MAX_TITLE_LENGTH = 80;

    @Value("${spring.ai.vectorstore.qdrant.collection-name:products}")
    private String collectionName;

    public ProductImportService(VectorStore vectorStore, EcomImportProperties importProperties,
            QdrantClient qdrantClient, CatalogCategoryRegistry categoryRegistry,
            CatalogReadiness catalogReadiness) {
        this.vectorStore = vectorStore;
        this.importProperties = importProperties;
        this.qdrantClient = qdrantClient;
        this.categoryRegistry = categoryRegistry;
        this.catalogReadiness = catalogReadiness;
    }

    public void importSampleProducts() {
        String csvContent = readCsvContent();
        List<Product> products = extractProducts(csvContent);
        if (products.isEmpty()) {
            throw new IllegalStateException("Catalog import produced no valid products; refusing to serve an empty catalog");
        }

        String desiredRevision = computeRevision(csvContent);
        products.forEach(product -> product.setCatalogRevision(desiredRevision));

        Map<String, Product> desired = new LinkedHashMap<>();
        products.forEach(product -> desired.put(product.getSku(), product));

        if (isComplete(desired, desiredRevision)) {
            log.info("Catalog already at revision {}; skipping import", desiredRevision);
            catalogReadiness.markVerified(desiredRevision);
            return;
        }

        // Upsert the full desired manifest first (this is where embedding work happens).
        vectorStore.add(products.stream().map(VectorStoreUtils::productToDocument).toList());

        if (!allDesiredPresent(desired, desiredRevision)) {
            throw new IllegalStateException(
                    "Catalog import verification failed for revision " + desiredRevision);
        }

        // Only after verification, remove obsolete/legacy points that are not part of the
        // desired manifest (never delete the legacy catalog before replacement succeeds).
        deletePointsNotIn(desired.keySet());

        catalogReadiness.markVerified(desiredRevision);
        log.info("Successfully imported {} products at revision {}", products.size(), desiredRevision);
    }

    private String readCsvContent() {
        ClassPathResource resource = new ClassPathResource(importProperties.sampleFile());
        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read CSV file " + importProperties.sampleFile(), e);
        }
    }

    private List<Product> extractProducts(String csvContent) {
        List<Product> products = new ArrayList<>();
        try (Reader reader = new java.io.StringReader(csvContent);
                CSVParser csvParser = CSVFormat.Builder.create().setHeader()
                        .setSkipHeaderRecord(true).setDelimiter(";").build().parse(reader)) {
            for (CSVRecord csvRecord : csvParser) {
                Product product = parseProductFromRecord(csvRecord);
                if (product != null) {
                    products.add(product);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse CSV", e);
        }
        return products;
    }

    Product parseProductFromRecord(CSVRecord record) {
        String sku = field(record, "sku");
        String title = field(record, "title");
        String categorySlug = field(record, "category");
        String categoryIdsCsv = field(record, "categoryIds");
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

        List<String> categoryIds = splitList(categoryIdsCsv);
        if (categoryIds.isEmpty()) {
            log.warn("Skipping CSV row with SKU {}: no browsing category ids", sku);
            return null;
        }
        for (String categoryId : categoryIds) {
            if (!categoryRegistry.isKnown(categoryId)) {
                log.warn("Skipping CSV row with SKU {}: unknown browsing category id '{}'", sku, categoryId);
                return null;
            }
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
        product.setCategoryIds(categoryIds);
        product.setCategoryPathIds(new ArrayList<>(categoryRegistry.categoryPathIds(categoryIds)));
        product.setBrowsingText(categoryRegistry.readableAncestry(categoryIds));
        return product;
    }

    private boolean isComplete(Map<String, Product> desired, String desiredRevision) {
        Map<String, String> stored = storedRevisions();
        if (stored.size() != desired.size()) {
            return false;
        }
        return allDesiredPresent(desired, desiredRevision);
    }

    private boolean allDesiredPresent(Map<String, Product> desired, String desiredRevision) {
        Map<String, String> stored = storedRevisions();
        for (String sku : desired.keySet()) {
            String id = VectorStoreUtils.skuPointId(sku);
            if (!desiredRevision.equals(stored.get(id))) {
                return false;
            }
        }
        return true;
    }

    private Map<String, String> storedRevisions() {
        Map<String, String> revisions = new HashMap<>();
        for (Points.RetrievedPoint point : scrollAll(null)) {
            revisions.put(point.getId().getUuid(), (String) payloadMap(point).get("catalogRevision"));
        }
        return revisions;
    }

    private void deletePointsNotIn(Set<String> desiredSkus) {
        Set<String> keepIds = desiredSkus.stream().map(VectorStoreUtils::skuPointId).collect(Collectors.toSet());
        List<Points.RetrievedPoint> points = scrollAll(null);
        List<Common.PointId> toDelete = new ArrayList<>();
        for (Points.RetrievedPoint point : points) {
            if (!keepIds.contains(point.getId().getUuid())) {
                toDelete.add(point.getId());
            }
        }
        if (!toDelete.isEmpty()) {
            try {
                qdrantClient.deleteAsync(collectionName, toDelete).get();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while cleaning up obsolete Qdrant points", e);
            }
            catch (ExecutionException e) {
                throw new IllegalStateException("Failed to clean up obsolete Qdrant points", e);
            }
            log.info("Deleted {} obsolete/legacy Qdrant points", toDelete.size());
        }
    }

    private List<Points.RetrievedPoint> scrollAll(Common.Filter filter) {
        List<Points.RetrievedPoint> all = new ArrayList<>();
        try {
            if (!qdrantClient.collectionExistsAsync(collectionName).get()) {
                return all;
            }
            Common.PointId offset = null;
            while (true) {
                Points.ScrollPoints.Builder builder = Points.ScrollPoints.newBuilder()
                        .setCollectionName(collectionName)
                        .setLimit(256)
                        .setWithPayload(WithPayloadSelectorFactory.enable(true));
                if (filter != null) {
                    builder.setFilter(filter);
                }
                if (offset != null) {
                    builder.setOffset(offset);
                }
                Points.ScrollResponse response = qdrantClient.scrollAsync(builder.build()).get();
                all.addAll(response.getResultList());
                if (response.hasNextPageOffset()) {
                    offset = response.getNextPageOffset();
                }
                else {
                    break;
                }
            }
            return all;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while scrolling Qdrant points", e);
        }
        catch (ExecutionException e) {
            throw new IllegalStateException("Failed to scroll Qdrant points", e);
        }
    }

    private static Map<String, Object> payloadMap(Points.RetrievedPoint point) {
        Map<String, Object> map = new HashMap<>();
        point.getPayloadMap().forEach((key, value) -> map.put(key, jsonValue(value)));
        return map;
    }

    private static Object jsonValue(io.qdrant.client.grpc.JsonWithInt.Value value) {
        switch (value.getKindCase()) {
            case STRING_VALUE:
                return value.getStringValue();
            case INTEGER_VALUE:
                return value.getIntegerValue();
            case DOUBLE_VALUE:
                return value.getDoubleValue();
            case BOOL_VALUE:
                return value.getBoolValue();
            case LIST_VALUE:
                return value.getListValue().getValuesList().stream()
                        .map(ProductImportService::jsonValue).toList();
            case NULL_VALUE:
                return null;
            default:
                return null;
        }
    }

    private String computeRevision(String csvContent) {
        String canonicalEdges = categoryRegistry.edges().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(edge -> edge.getKey() + "->" + edge.getValue())
                .collect(Collectors.joining("\n"));
        return sha256Hex(csvContent + "\n" + canonicalEdges);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static List<String> splitList(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
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
