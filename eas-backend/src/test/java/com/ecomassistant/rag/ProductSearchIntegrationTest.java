package com.ecomassistant.rag;

import com.ecomassistant.guardrails.QueryAnalysis;
import com.ecomassistant.support.OpenAiWireMockStubs;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for the product-search pipeline: {@code GET /api/v1/search} over a real
 * HTTP connection with Testcontainers Qdrant, the {@code FakeEmbeddingModel} and WireMock
 * stubs for the single query-analysis LLM call. The retrieved products are returned
 * directly, without a product-filtering call.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProductSearchIntegrationTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Container
    @ServiceConnection
    static final QdrantContainer qdrant = new QdrantContainer("qdrant/qdrant:v1.13.4");

    static final WireMockServer wireMock =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        wireMock.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.vectorstore.type", () -> "qdrant");
        registry.add("spring.ai.model.chat", () -> "deepseek");
        registry.add("spring.ai.model.embedding", () -> "none");
        registry.add("spring.ai.deepseek.api-key", () -> "test-key");
        registry.add("spring.ai.deepseek.base-url", wireMock::baseUrl);
        registry.add("ecom.import.sample-file", () -> "sampledata/test-apparel.csv");
        // Keep the rate limiter and daily budget out of the way of these pipeline tests.
        registry.add("ecom.limits.per-minute", () -> "1000000");
        registry.add("ecom.limits.per-hour", () -> "1000000");
        registry.add("ecom.limits.daily-budget", () -> "1000000");
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @LocalServerPort
    int port;

    @Autowired
    RestClient.Builder restClientBuilder;

    @Test
    void happyPathReturnsProductsWithFieldsAndInterpretation() throws Exception {
        OpenAiWireMockStubs.stubQueryAnalysis(wireMock, new QueryAnalysis(true, List.of(), List.of(),
                "rain jacket", List.of(), null, false));

        HttpResult result = get("blue cotton jacket", java.util.UUID.randomUUID().toString());

        assertThat(result.status()).isEqualTo(HttpStatus.OK.value());
        JsonNode root = JSON_MAPPER.readTree(result.body());

        assertThat(skus(root)).contains("RAIN1");

        JsonNode rain1 = productBySku(root, "RAIN1");
        assertThat(rain1.get("title").asText()).isEqualTo("Lightweight Rain Shell");
        assertThat(rain1.get("category").asText()).isEqualTo("Rain Jackets");
        assertThat(rain1.get("colors")).hasSize(1);
        assertThat(rain1.get("colors").get(0).asText()).isEqualTo("blue");
        assertThat(rain1.get("sizes")).hasSize(1);
        assertThat(rain1.get("sizes").get(0).asText()).isEqualTo("M");
        assertThat(rain1.get("imageUrl").asText()).isEqualTo("/images/categories/rain.webp");
        assertThat(rain1.has("imageKey")).isFalse();

        JsonNode interpretation = root.get("interpretation");
        assertThat(interpretation).isNotNull();
        assertThat(interpretation.get("searchQuery").asText()).isEqualTo("rain jacket");
        assertThat(root.get("suggestedPrompts").isNull()).isTrue();

        // Exactly one LLM call: the query analysis.
        assertThat(requests()).hasSize(1);
        assertThat(requests().get(0).getBodyAsString()).contains(OpenAiWireMockStubs.QUERY_ANALYSIS_MARKER);
    }

    @Test
    void lowestPriceOrderingIsPreservedInResponse() throws Exception {
        OpenAiWireMockStubs.stubQueryAnalysis(wireMock, new QueryAnalysis(true, List.of(), List.of(),
                "rain jacket", List.of(), null, true));

        HttpResult result = get("cheapest rain jacket", java.util.UUID.randomUUID().toString());

        assertThat(result.status()).isEqualTo(HttpStatus.OK.value());
        JsonNode root = JSON_MAPPER.readTree(result.body());
        assertThat(prices(root)).isNotEmpty();
        assertThat(prices(root)).isSorted();
    }

    @Test
    void rejectedRequestReturns422WithSingleCall() throws Exception {
        OpenAiWireMockStubs.stubQueryAnalysis(wireMock, new QueryAnalysis(false,
                List.of("not a clothing search"), List.of("winter jacket under 200 euro"), "", List.of(), null,
                false));

        HttpResult result = get("tell me a joke", java.util.UUID.randomUUID().toString());

        assertThat(result.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(requests()).hasSize(1);
    }

    @Test
    void emptyRetrievalReturnsEmptyProductsWithInterpretation() throws Exception {
        OpenAiWireMockStubs.stubQueryAnalysis(wireMock, new QueryAnalysis(true, List.of(), List.of(),
                "zzzz nomatch", List.of(), null, false));

        HttpResult result = get("zzzz nomatch", java.util.UUID.randomUUID().toString());

        assertThat(result.status()).isEqualTo(HttpStatus.OK.value());
        JsonNode root = JSON_MAPPER.readTree(result.body());
        assertThat(skus(root)).isEmpty();
        assertThat(root.get("interpretation")).isNotNull();
        assertThat(root.get("interpretation").get("searchQuery").asText()).isEqualTo("zzzz nomatch");
        assertThat(requests()).hasSize(1);
    }

    @Test
    void nonEnglishRejectionPassesSuggestionsThroughUnchanged() throws Exception {
        QueryAnalysis analysis = new QueryAnalysis(false,
                List.of("The request is contradictory: a winter jacket cannot be for summer."),
                List.of("лёгкая летняя куртка", "тёплая зимняя куртка"), "", List.of(), null, false);
        OpenAiWireMockStubs.stubQueryAnalysis(wireMock, analysis);

        HttpResult result = get("зимняя куртка для лета", java.util.UUID.randomUUID().toString());

        assertThat(result.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        JsonNode root = JSON_MAPPER.readTree(result.body());
        assertThat(root.get("products").isNull()).isTrue();
        assertThat(root.get("interpretation").isNull()).isTrue();
        assertThat(root.get("suggestedPrompts")).hasSize(2);
        assertThat(root.get("suggestedPrompts").get(0).asText()).isEqualTo("лёгкая летняя куртка");
        assertThat(root.get("suggestedPrompts").get(1).asText()).isEqualTo("тёплая зимняя куртка");
    }

    private HttpResult get(String message, String conversationId) {
        return restClientBuilder.baseUrl("http://localhost:" + port)
            .build()
            .get()
            .uri(uriBuilder -> uriBuilder.path("/api/v1/search")
                .queryParam("message", message)
                .queryParam("conversationId", conversationId)
                .build())
            .exchange((request, response) -> new HttpResult(response.getStatusCode().value(), readBody(response)));
    }

    private static String readBody(org.springframework.http.client.ClientHttpResponse response) {
        try (var body = response.getBody()) {
            return body == null ? "" : new String(body.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (Exception e) {
            return "";
        }
    }

    private static List<LoggedRequest> requests() {
        return wireMock.getAllServeEvents().stream().map(event -> event.getRequest()).toList();
    }

    private static List<String> skus(JsonNode root) {
        List<String> result = new ArrayList<>();
        for (JsonNode product : root.get("products")) {
            result.add(product.get("sku").asText());
        }
        return result;
    }

    private static JsonNode productBySku(JsonNode root, String sku) {
        for (JsonNode product : root.get("products")) {
            if (product.get("sku").asText().equals(sku)) {
                return product;
            }
        }
        return null;
    }

    private static List<Double> prices(JsonNode root) {
        List<Double> result = new ArrayList<>();
        for (JsonNode product : root.get("products")) {
            result.add(product.get("price").asDouble());
        }
        return result;
    }

    private record HttpResult(int status, String body) {
    }
}
