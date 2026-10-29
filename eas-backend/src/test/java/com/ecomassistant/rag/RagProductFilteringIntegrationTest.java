package com.ecomassistant.rag;

import com.ecomassistant.guardrails.PriceConstraint;
import com.ecomassistant.guardrails.PriceOperator;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for the RAG product-filtering pipeline: {@code GET /api/v1/search} over a
 * real HTTP connection with Testcontainers Qdrant, the {@code FakeEmbeddingModel}
 * and WireMock stubs for both LLM calls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RagProductFilteringIntegrationTest {

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
        registry.add("spring.ai.model.chat", () -> "openai");
        registry.add("spring.ai.model.embedding", () -> "none");
        registry.add("spring.ai.openai.api-key", () -> "test-key");
        registry.add("spring.ai.openai.base-url", wireMock::baseUrl);
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
    void happyPathReturnsProductsWithNewFieldsAndInterpretation() throws Exception {
        OpenAiWireMockStubs.stubQueryAnalysis(wireMock, new QueryAnalysis(true, List.of(), List.of(),
                "rain jacket", List.of("wool"), new PriceConstraint(PriceOperator.LESS_THAN, new BigDecimal("200")),
                false));
        OpenAiWireMockStubs.stubProductSelection(wireMock,
                new ProductSelection(List.of("HALLUCINATED999", "RAIN2", "RAIN1")));

        HttpResult result = get("blue cotton jacket under 200 euro, no wool", java.util.UUID.randomUUID().toString());

        assertThat(result.status()).isEqualTo(HttpStatus.OK.value());
        JsonNode root = JSON_MAPPER.readTree(result.body());
        assertThat(skus(root)).containsExactly(retrievalOrder(productFilterBody(), "RAIN1", "RAIN2"));
        assertThat(skus(root)).doesNotContain("HALLUCINATED999");

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
        assertThat(interpretation.get("negativeExamples")).hasSize(1);
        assertThat(interpretation.get("negativeExamples").get(0).asText()).isEqualTo("wool");
        assertThat(interpretation.get("priceConstraint").get("operator").asText()).isEqualTo("LESS_THAN");
        assertThat(interpretation.get("priceConstraint").get("value").asDouble()).isEqualTo(200.0);
        assertThat(interpretation.get("sortByLowestPrice").asBoolean()).isFalse();
        assertThat(root.get("suggestedPrompts").isNull()).isTrue();

        List<LoggedRequest> requests = requests();
        assertThat(requests).hasSize(2);
        assertThat(requests).anySatisfy(request -> assertThat(request.getBodyAsString())
            .contains(OpenAiWireMockStubs.QUERY_ANALYSIS_MARKER));
        String productFilterBody = productFilterBody();
        assertThat(productFilterBody).contains("wool", "RAIN1", "RAIN2");
        assertThat(productFilterBody).doesNotContain("HALLUCINATED999", "RAIN3");
    }

    @Test
    void lowestPriceOrderingIsPreservedInResponse() throws Exception {
        OpenAiWireMockStubs.stubQueryAnalysis(wireMock, new QueryAnalysis(true, List.of(), List.of(),
                "rain jacket", List.of(), null, true));
        OpenAiWireMockStubs.stubProductSelection(wireMock, new ProductSelection(List.of("RAIN3", "RAIN1")));

        HttpResult result = get("cheapest rain jacket", java.util.UUID.randomUUID().toString());

        assertThat(result.status()).isEqualTo(HttpStatus.OK.value());
        JsonNode root = JSON_MAPPER.readTree(result.body());
        assertThat(skus(root)).containsExactly("RAIN1", "RAIN3");
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
    void refinementReturnsSecondAnalysisSearchQuery() throws Exception {
        QueryAnalysis first = new QueryAnalysis(true, List.of(), List.of(),
                "warm winter jacket", List.of(), null, false);
        QueryAnalysis second = new QueryAnalysis(true, List.of(), List.of(),
                "warm navy winter jacket", List.of(), null, false);

        OpenAiWireMockStubs.stubChatCompletionForMarkerSequence(wireMock, OpenAiWireMockStubs.QUERY_ANALYSIS_MARKER,
                OpenAiWireMockStubs.queryAnalysisJson(first), OpenAiWireMockStubs.queryAnalysisJson(second));
        OpenAiWireMockStubs.stubProductSelection(wireMock, new ProductSelection(List.of()));

        get("warm winter jacket", "refine-conv");
        HttpResult secondResult = get("same but in navy", "refine-conv");

        assertThat(secondResult.status()).isEqualTo(HttpStatus.OK.value());
        JsonNode root = JSON_MAPPER.readTree(secondResult.body());
        assertThat(root.get("interpretation").get("searchQuery").asText()).isEqualTo("warm navy winter jacket");
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

    @Test
    void persistentInvalidSelectionReturns500() throws Exception {
        OpenAiWireMockStubs.stubQueryAnalysis(wireMock, new QueryAnalysis(true, List.of(), List.of(),
                "rain jacket", List.of(), null, false));
        OpenAiWireMockStubs.stubChatCompletionForMarker(wireMock, OpenAiWireMockStubs.PRODUCT_FILTER_MARKER,
                "{\"skus\": 123}");

        HttpResult result = get("rain jacket", java.util.UUID.randomUUID().toString());

        assertThat(result.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        JsonNode root = JSON_MAPPER.readTree(result.body());
        assertThat(root.get("error").asText()).isEqualTo("Illegal assistant response");
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

    private static String productFilterBody() {
        return requests().stream()
            .map(LoggedRequest::getBodyAsString)
            .filter(body -> body.contains(OpenAiWireMockStubs.PRODUCT_FILTER_MARKER))
            .findFirst()
            .orElseThrow();
    }

    /** Orders the given SKUs by their position in the candidate list of the filtering prompt. */
    private static String[] retrievalOrder(String productFilterBody, String... skus) {
        return Arrays.stream(skus)
            .sorted(Comparator.comparingInt(productFilterBody::indexOf))
            .toArray(String[]::new);
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
