package com.ecomassistant.health;

import com.ecomassistant.retrieval.FakeEmbeddingModel;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Readiness endpoint behaviour over a real HTTP connection: it reports {@code UP} only
 * once the application is ready, exposes nothing but the {@code status} field, and never
 * calls the LLM or the embedding model.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ServiceHealthIntegrationTest {

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
    }

    @LocalServerPort
    int port;

    @Autowired
    FakeEmbeddingModel fakeEmbeddingModel;

    @Test
    void healthReturnsUpWithOnlyStatusWhenStarted() throws Exception {
        HealthResult result = probeHealth();

        assertThat(result.status()).isEqualTo(200);
        JsonNode body = JSON_MAPPER.readTree(result.body());
        assertThat(body.get("status").asText()).isEqualTo("UP");
        assertThat(body.size()).isEqualTo(1);
    }

    @Test
    void healthProbeMakesNoLlmOrEmbeddingCalls() throws Exception {
        wireMock.resetAll();
        long embedCountBefore = fakeEmbeddingModel.getEmbedCount();

        for (int i = 0; i < 20; i++) {
            assertThat(probeHealth().status()).isEqualTo(200);
        }

        assertThat(wireMock.getAllServeEvents()).isEmpty();
        assertThat(fakeEmbeddingModel.getEmbedCount()).isEqualTo(embedCountBefore);
    }

    private HealthResult probeHealth() {
        return RestClient.create()
            .get()
            .uri("http://localhost:" + port + "/api/v1/health")
            .exchange((request, response) -> new HealthResult(
                response.getStatusCode().value(),
                readBody(response)));
    }

    private static String readBody(ClientHttpResponse response) {
        try (var body = response.getBody()) {
            return body == null ? "" : new String(body.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private record HealthResult(int status, String body) {
    }
}
