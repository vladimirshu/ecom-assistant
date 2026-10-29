package com.ecomassistant.limits;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

/**
 * Base class for search rate-limit and daily-budget integration tests: Testcontainers
 * Qdrant, the {@code FakeEmbeddingModel}, a WireMock server standing in for the OpenAI
 * chat-completions endpoint, and MockMvc to exercise {@code GET /api/v1/search}. The
 * limits keep their configured defaults; subclasses override as needed.
 */
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
public abstract class AbstractSearchLimitIntegrationTest {

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

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @Autowired
    protected MockMvc mockMvc;
}
