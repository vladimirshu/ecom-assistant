package com.ecomassistant.service;

import com.ecomassistant.VectorStoreUtils;
import com.ecomassistant.entity.Product;
import com.ecomassistant.repository.ProductRepository;
import com.ecomassistant.retrieval.FakeEmbeddingModel;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.Points;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the idempotent catalog import, running against its own
 * Testcontainers MySQL + Qdrant with the {@code FakeEmbeddingModel} so assertions about
 * embedding work and vector-store contents are meaningful.
 */
@SpringBootTest
@Testcontainers
class ProductImportServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final QdrantContainer qdrant = new QdrantContainer("qdrant/qdrant:v1.13.4");

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.vectorstore.type", () -> "qdrant");
        registry.add("spring.ai.model.chat", () -> "openai");
        registry.add("spring.ai.model.embedding", () -> "none");
        registry.add("spring.ai.openai.api-key", () -> "test-key");
        registry.add("ecom.import.sample-file", () -> "sampledata/test-apparel.csv");
    }

    @Autowired
    private ProductImportService productImportService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private QdrantClient qdrantClient;

    @Autowired
    private FakeEmbeddingModel fakeEmbeddingModel;

    @Value("${spring.ai.vectorstore.qdrant.collection-name:products}")
    private String collectionName;

    @BeforeEach
    void cleanState() {
        productRepository.deleteAll();
        clearVectorStore();
        fakeEmbeddingModel.resetEmbedCount();
    }

    @Test
    void freshImportStoresAllProductsInBothStores() {
        productImportService.importSampleProducts();

        assertThat(productRepository.count()).isEqualTo(8);
        assertThat(qdrantPointCount()).isEqualTo(8);
        assertThat(fakeEmbeddingModel.getEmbedCount()).isGreaterThan(0);
    }

    @Test
    void restartWithCurrentDataDoesNoEmbeddingWork() {
        productImportService.importSampleProducts();
        fakeEmbeddingModel.resetEmbedCount();

        productImportService.importSampleProducts();

        assertThat(fakeEmbeddingModel.getEmbedCount()).isZero();
        assertThat(productRepository.count()).isEqualTo(8);
        assertThat(qdrantPointCount()).isEqualTo(8);
    }

    @Test
    void upgradeFromRowsWithoutCategoryLeavesExactlyOnePointPerSku() {
        productImportService.importSampleProducts();

        // Simulate the previous schema: no category in MySQL, plus a leftover Qdrant
        // point (random id) without a category payload.
        clearCategories();
        addLegacyPoint("RAIN1");

        productImportService.importSampleProducts();

        assertThat(productRepository.existsByCategoryIsNull()).isFalse();
        assertThat(productRepository.count()).isEqualTo(8);
        assertThat(qdrantPointCount()).isEqualTo(8);
    }

    @Test
    void legacyRowMissingFromCsvIsRemovedSoImportDoesNotRepeat() {
        productImportService.importSampleProducts();

        Product orphan = new Product();
        orphan.setSku("LEGACY1");
        orphan.setColors("blue");
        orphan.setSizes("M");
        orphan.setPrice(new java.math.BigDecimal("10.00"));
        orphan.setDescription("legacy row without category");
        productRepository.save(orphan);

        productImportService.importSampleProducts();

        assertThat(productRepository.existsByCategoryIsNull()).isFalse();
        assertThat(productRepository.existsById("LEGACY1")).isFalse();
        assertThat(productRepository.count()).isEqualTo(8);

        fakeEmbeddingModel.resetEmbedCount();
        productImportService.importSampleProducts();
        assertThat(fakeEmbeddingModel.getEmbedCount()).isZero();
    }

    @Test
    void documentRoundTripKeepsAllAttributes() {
        productImportService.importSampleProducts();

        Product product = productRepository.findById("RAIN1").orElseThrow();
        Document document = VectorStoreUtils.productToDocument(product);
        Product roundTripped = VectorStoreUtils.documentToProduct(document);

        assertThat(roundTripped.getSku()).isEqualTo(product.getSku());
        assertThat(roundTripped.getTitle()).isEqualTo(product.getTitle());
        assertThat(roundTripped.getCategory()).isEqualTo(product.getCategory());
        assertThat(roundTripped.getColors()).isEqualTo(product.getColors());
        assertThat(roundTripped.getSizes()).isEqualTo(product.getSizes());
        assertThat(roundTripped.getPrice()).isEqualByComparingTo(product.getPrice());
        assertThat(roundTripped.getDescription()).isEqualTo(product.getDescription());
        assertThat(roundTripped.getImageKey()).isEqualTo(product.getImageKey());
    }

    private void clearCategories() {
        List<Product> products = productRepository.findAll();
        for (Product product : products) {
            product.setCategory(null);
        }
        productRepository.saveAll(products);
    }

    private void addLegacyPoint(String sku) {
        Document legacy = Document.builder()
            .id(UUID.randomUUID().toString())
            .text("legacy " + sku)
            .metadata(Map.of(
                    "sku", sku,
                    "colors", "blue",
                    "sizes", "M",
                    "price", 50.0,
                    "description", "legacy description"))
            .build();
        vectorStore.add(List.of(legacy));
    }

    private long qdrantPointCount() {
        try {
            if (!qdrantClient.collectionExistsAsync(collectionName).get()) {
                return 0;
            }
            return qdrantClient.countAsync(collectionName, null, true).get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        catch (ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    private void clearVectorStore() {
        try {
            if (!qdrantClient.collectionExistsAsync(collectionName).get()) {
                return;
            }
            Points.ScrollPoints request = Points.ScrollPoints.newBuilder()
                .setCollectionName(collectionName)
                .setLimit(1000)
                .build();
            Points.ScrollResponse response = qdrantClient.scrollAsync(request).get();
            List<Common.PointId> ids = response.getResultList().stream()
                .map(Points.RetrievedPoint::getId)
                .toList();
            if (!ids.isEmpty()) {
                qdrantClient.deleteAsync(collectionName, ids).get();
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        catch (ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }
}
