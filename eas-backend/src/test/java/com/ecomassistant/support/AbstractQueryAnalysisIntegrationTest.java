package com.ecomassistant.support;

import com.ecomassistant.retrieval.AbstractRetrievalIntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for query-analysis integration tests: Testcontainers Qdrant/MySQL, the
 * {@code FakeEmbeddingModel}, a WireMock server standing in for the OpenAI chat
 * completions endpoint, and a MockMvc client to exercise {@code GET /search}.
 */
@AutoConfigureMockMvc
public abstract class AbstractQueryAnalysisIntegrationTest extends AbstractRetrievalIntegrationTest {

    protected static final WireMockServer wireMock =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        wireMock.start();
    }

    @DynamicPropertySource
    static void openAiBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", wireMock::baseUrl);
    }

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @Autowired
    protected MockMvc mockMvc;
}
