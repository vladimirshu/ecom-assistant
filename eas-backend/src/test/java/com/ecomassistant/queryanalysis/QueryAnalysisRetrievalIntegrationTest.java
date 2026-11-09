package com.ecomassistant.queryanalysis;

import com.ecomassistant.guardrails.PriceConstraint;
import com.ecomassistant.guardrails.PriceOperator;
import com.ecomassistant.guardrails.QueryAnalysis;
import com.ecomassistant.support.AbstractQueryAnalysisIntegrationTest;
import com.ecomassistant.support.OpenAiWireMockStubs;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Happy-path integration test: an allowed query analysis is mapped to retrieval
 * parameters, products are retrieved through the real Qdrant retriever and returned
 * directly, without a second LLM call.
 */
class QueryAnalysisRetrievalIntegrationTest extends AbstractQueryAnalysisIntegrationTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Test
    void allowedRequestReturnsProductsFilteredByPrice() throws Exception {
        OpenAiWireMockStubs.stubQueryAnalysis(wireMock, new QueryAnalysis(true, List.of(), List.of(), "jacket",
                List.of(), new PriceConstraint(PriceOperator.LESS_THAN_OR_EQUAL, new BigDecimal("150")), false));

        MvcResult result = mockMvc.perform(get("/api/v1/search")
                        .param("message", "I want a jacket up to 150 euro")
                        .param("conversationId", java.util.UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = JSON_MAPPER.readTree(result.getResponse().getContentAsString());
        List<String> skus = new ArrayList<>();
        List<Double> prices = new ArrayList<>();
        for (JsonNode product : root.get("products")) {
            skus.add(product.get("sku").asText());
            prices.add(product.get("price").asDouble());
        }

        // The price constraint is applied at retrieval, so every returned product is within
        // budget and the over-budget candidates never reach the response.
        assertThat(prices).isNotEmpty();
        assertThat(prices).allMatch(price -> price <= 150.0);
        assertThat(skus).doesNotContain("RAIN3", "DENIM1", "WOOL1", "WOOL3");
    }

    @Test
    void allowedRequestMakesExactlyOneLlmCall() throws Exception {
        OpenAiWireMockStubs.stubQueryAnalysis(wireMock, new QueryAnalysis(true, List.of(), List.of(), "rain jacket",
                List.of(), null, false));

        mockMvc.perform(get("/api/v1/search")
                        .param("message", "show me a rain jacket")
                        .param("conversationId", java.util.UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        List<LoggedRequest> requests = wireMock.getAllServeEvents().stream()
                .map(event -> event.getRequest())
                .toList();
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getBodyAsString()).contains(OpenAiWireMockStubs.QUERY_ANALYSIS_MARKER);
    }

    @Test
    void queryAnalysisCarriesConfiguredOutputTokenLimit() throws Exception {
        OpenAiWireMockStubs.stubQueryAnalysis(wireMock, new QueryAnalysis(true, List.of(), List.of(), "rain jacket",
                List.of(), null, false));

        mockMvc.perform(get("/api/v1/search")
                        .param("message", "show me a rain jacket")
                        .param("conversationId", java.util.UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        JsonNode queryAnalysisBody = bodyContaining(OpenAiWireMockStubs.QUERY_ANALYSIS_MARKER);
        assertThat(queryAnalysisBody.get("max_tokens").asInt()).isEqualTo(500);
    }

    @Test
    void typicalFullResponseFitsTheCap() throws Exception {
        QueryAnalysis full = new QueryAnalysis(true, List.of(), List.of(), "waterproof hiking jacket",
                List.of("wool", "down"), new PriceConstraint(PriceOperator.LESS_THAN, new BigDecimal("150")), true);
        OpenAiWireMockStubs.stubQueryAnalysis(wireMock, full);

        MvcResult result = mockMvc.perform(get("/api/v1/search")
                        .param("message", "a waterproof hiking jacket under 150 euro, no wool or down, cheapest first")
                        .param("conversationId", java.util.UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode interpretation = JSON_MAPPER.readTree(result.getResponse().getContentAsString())
                .get("interpretation");
        assertThat(interpretation.get("searchQuery").asText()).isEqualTo("waterproof hiking jacket");
        assertThat(interpretation.get("negativeExamples")).hasSize(2);
        assertThat(interpretation.get("priceConstraint").get("value").asDouble()).isEqualTo(150.0);
        assertThat(interpretation.get("sortByLowestPrice").asBoolean()).isTrue();
    }

    private JsonNode bodyContaining(String marker) throws Exception {
        String body = wireMock.getAllServeEvents().stream()
                .map(event -> event.getRequest().getBodyAsString())
                .filter(b -> b.contains(marker))
                .findFirst()
                .orElseThrow();
        return JSON_MAPPER.readTree(body);
    }
}
