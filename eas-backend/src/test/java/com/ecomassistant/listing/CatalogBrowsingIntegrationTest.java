package com.ecomassistant.listing;

import com.ecomassistant.catalog.CatalogCategoryRegistry;
import com.ecomassistant.retrieval.FakeEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract tests for deterministic, no-LLM catalog listing, running against the imported
 * {@code test-browsing.csv} fixture (57 men's jeans plus a small cross-root assortment).
 */
@SpringBootTest
@Testcontainers
class CatalogBrowsingIntegrationTest {

    @Container
    @ServiceConnection
    static final QdrantContainer qdrant = new QdrantContainer("qdrant/qdrant:v1.13.4");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.vectorstore.type", () -> "qdrant");
        registry.add("spring.ai.model.chat", () -> "openai");
        registry.add("spring.ai.model.embedding", () -> "none");
        registry.add("spring.ai.openai.api-key", () -> "test-key");
        registry.add("ecom.import.sample-file", () -> "sampledata/test-browsing.csv");
        registry.add("ecom.limits.per-minute", () -> "1000000");
        registry.add("ecom.limits.per-hour", () -> "1000000");
        registry.add("ecom.limits.daily-budget", () -> "1000000");
    }

    @Autowired
    private CatalogListingService listingService;

    @Autowired
    private CatalogReadiness catalogReadiness;

    @Autowired
    private CatalogCursorCodec cursorCodec;

    @Autowired
    private CatalogCategoryRegistry categoryRegistry;

    @Autowired
    private FakeEmbeddingModel fakeEmbeddingModel;

    @Test
    void fiftySevenProductTraversalInPagesOf24() {
        CatalogPage p1 = listingService.list("men-jeans", 24, null);
        CatalogPage p2 = listingService.list("men-jeans", 24, p1.nextCursor());
        CatalogPage p3 = listingService.list("men-jeans", 24, p2.nextCursor());

        assertThat(skus(p1)).hasSize(24);
        assertThat(skus(p2)).hasSize(24);
        assertThat(skus(p3)).hasSize(9);
        assertThat(p1.nextCursor()).isNotNull();
        assertThat(p2.nextCursor()).isNotNull();
        assertThat(p3.nextCursor()).isNull();

        Set<String> all = new LinkedHashSet<>();
        all.addAll(skus(p1));
        all.addAll(skus(p2));
        all.addAll(skus(p3));
        assertThat(all).hasSize(57);
        assertThat(all).allMatch(sku -> sku.startsWith("M-JEANS-"));
    }

    @Test
    void traversalOrderIsDeterministic() {
        CatalogPage p1 = listingService.list("men-jeans", 24, null);
        assertThat(skus(p1)).isSorted();

        CatalogPage again = listingService.list("men-jeans", 24, null);
        assertThat(skus(again)).isEqualTo(skus(p1));
    }

    @Test
    void rootIntermediateLeafAndDirectMembership() {
        assertThat(skus(listingService.list("men-rain", 100, null))).containsExactly("M-RAIN-1");
        assertThat(skus(listingService.list("men-jackets", 100, null)))
                .containsExactlyInAnyOrder("M-RAIN-1", "M-JACKETS-1");
        assertThat(skus(listingService.list("men-jackets-coats", 100, null)))
                .contains("M-RAIN-1", "M-COAT-1", "M-JACKETS-1");
        assertThat(skus(listingService.list("men", 100, null))).contains("M-ROOT-1");
    }

    @Test
    void rootSelectionExcludesOtherRootOnlyProducts() {
        List<String> women = skus(listingService.list("women", 100, null));
        assertThat(women).contains("W-JEANS-1", "W-SWEATER-1", "W-COAT-1", "U-HOODIE-1");
        assertThat(women).doesNotContain("M-JEANS-001", "M-RAIN-1", "M-COAT-1", "M-ROOT-1");

        List<String> men = skus(listingService.list("men", 100, null));
        assertThat(men).contains("M-JEANS-001", "M-RAIN-1", "M-COAT-1", "M-ROOT-1", "U-HOODIE-1");
        assertThat(men).doesNotContain("W-JEANS-1", "W-SWEATER-1", "W-COAT-1");
    }

    @Test
    void sharedProductAppearsOnceInUnfilteredTraversal() {
        List<String> all = skus(listingService.list(null, 100, null));
        assertThat(all.stream().filter("U-HOODIE-1"::equals).count()).isEqualTo(1);
    }

    @Test
    void knownEmptyCategoryReturnsEmptyPageAndNullCursor() {
        CatalogPage page = listingService.list("men-parka", 24, null);
        assertThat(page.products()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void listingMakesNoEmbeddingCalls() {
        fakeEmbeddingModel.resetEmbedCount();

        listingService.list("men-jeans", 24, null);

        assertThat(fakeEmbeddingModel.getEmbedCount()).isZero();
    }

    @Test
    void listingUsesTheSearchProductRepresentation() {
        CatalogPage page = listingService.list("men-rain", 100, null);

        var product = page.products().get(0);
        assertThat(product.sku()).isEqualTo("M-RAIN-1");
        assertThat(product.category()).isEqualTo("Rain Jackets");
        assertThat(product.colors()).containsExactly("navy");
        assertThat(product.sizes()).containsExactly("M", "L");
        assertThat(product.price()).isEqualByComparingTo(new java.math.BigDecimal("120.00"));
        assertThat(product.imageUrl()).isEqualTo("/images/categories/rain.webp");
    }

    @Test
    void unknownCategoryIsRejected() {
        assertThatThrownBy(() -> listingService.list("unknown-branch", 24, null))
                .isInstanceOf(CatalogRequestException.class);
    }

    @Test
    void invalidLimitIsRejected() {
        assertThatThrownBy(() -> listingService.list(null, 0, null))
                .isInstanceOf(CatalogRequestException.class);
        assertThatThrownBy(() -> listingService.list(null, 101, null))
                .isInstanceOf(CatalogRequestException.class);
    }

    @Test
    void cursorBoundToAnotherCategoryIsRejected() {
        String revision = catalogReadiness.verifiedRevision();
        String menCursor = cursorCodec.encode(new CatalogCursor(revision, "men", 24, 0, now()));

        assertThatThrownBy(() -> listingService.list("women", 24, menCursor))
                .isInstanceOf(CatalogRequestException.class);
    }

    @Test
    void cursorBoundToAnotherLimitIsRejected() {
        String revision = catalogReadiness.verifiedRevision();
        String cursor = cursorCodec.encode(new CatalogCursor(revision, "men-jeans", 24, 0, now()));

        assertThatThrownBy(() -> listingService.list("men-jeans", 12, cursor))
                .isInstanceOf(CatalogRequestException.class);
    }

    @Test
    void cursorFromEarlierRevisionIsRejected() {
        String stale = cursorCodec.encode(new CatalogCursor("old-revision", "men-jeans", 24, 0, now()));

        assertThatThrownBy(() -> listingService.list("men-jeans", 24, stale))
                .isInstanceOf(CatalogRequestException.class);
    }

    @Test
    void expiredCursorIsRejected() {
        String revision = catalogReadiness.verifiedRevision();
        String expired = cursorCodec.encode(new CatalogCursor(revision, "men-jeans", 24, 0, now() - 7200));

        assertThatThrownBy(() -> listingService.list("men-jeans", 24, expired))
                .isInstanceOf(CatalogRequestException.class);
    }

    @Test
    void tamperedCursorIsRejected() {
        String revision = catalogReadiness.verifiedRevision();
        String token = cursorCodec.encode(new CatalogCursor(revision, "men-jeans", 24, 0, now()));
        int dot = token.lastIndexOf('.');
        char firstSigChar = token.charAt(dot + 1);
        char flipped = firstSigChar == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, dot + 1) + flipped + token.substring(dot + 2);

        assertThatThrownBy(() -> listingService.list("men-jeans", 24, tampered))
                .isInstanceOf(CatalogRequestException.class);
    }

    private static List<String> skus(CatalogPage page) {
        return page.products().stream().map(p -> p.sku()).toList();
    }

    private static long now() {
        return System.currentTimeMillis() / 1000;
    }
}
