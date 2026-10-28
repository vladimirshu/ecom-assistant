package com.ecomassistant.queryanalysis;

import com.ecomassistant.guardrails.PriceConstraint;
import com.ecomassistant.guardrails.PriceOperator;
import com.ecomassistant.guardrails.QueryAnalysis;
import com.ecomassistant.rag.ProductSelection;
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
 * parameters, products are retrieved through the real Qdrant retriever and then filtered
 * by the second LLM call.
 */
class QueryAnalysisRetrievalIntegrationTest extends AbstractQueryAnalysisIntegrationTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Test
    void allowedRequestReturnsProductsFilteredByPrice() throws Exception {
        OpenAiWireMockStubs.stubQueryAnalysis(wireMock, new QueryAnalysis(true, List.of(), List.of(), "jacket",
                List.of(), new PriceConstraint(PriceOperator.LESS_THAN_OR_EQUAL, new BigDecimal("150")), false));
        // The selection also names over-budget SKUs: they must not survive, because the price
        // filter keeps them out of retrieval and the SKU guardrail drops unretrieved SKUs.
        OpenAiWireMockStubs.stubProductSelection(wireMock,
                new ProductSelection(List.of("RAIN1", "RAIN2", "RAIN3", "DENIM1")));

        MvcResult result = mockMvc.perform(get("/search")
                        .param("message", "I want a jacket up to 150 euro")
                        .param("conversationId", "session-1"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = JSON_MAPPER.readTree(result.getResponse().getContentAsString());
        List<String> skus = new ArrayList<>();
        List<Double> prices = new ArrayList<>();
        for (JsonNode product : root.get("products")) {
            skus.add(product.get("sku").asText());
            prices.add(product.get("price").asDouble());
        }

        assertThat(prices).isNotEmpty();
        assertThat(prices).allMatch(price -> price <= 150.0);
        assertThat(skus).containsExactlyInAnyOrder("RAIN1", "RAIN2");
        assertThat(skus).doesNotContain("RAIN3", "DENIM1");

        String productFilterBody = wireMock.getAllServeEvents().stream()
                .map(event -> event.getRequest().getBodyAsString())
                .filter(body -> body.contains(OpenAiWireMockStubs.PRODUCT_FILTER_MARKER))
                .findFirst()
                .orElseThrow();
        assertThat(productFilterBody).contains("RAIN1", "RAIN2");
        assertThat(productFilterBody).doesNotContain("RAIN3", "DENIM1");
    }

    @Test
    void allowedRequestMakesExactlyTwoLlmCalls() throws Exception {
        OpenAiWireMockStubs.stubQueryAnalysis(wireMock, new QueryAnalysis(true, List.of(), List.of(), "rain jacket",
                List.of(), null, false));
        OpenAiWireMockStubs.stubProductSelection(wireMock, new ProductSelection(List.of("RAIN1")));

        mockMvc.perform(get("/search")
                        .param("message", "show me a rain jacket")
                        .param("conversationId", "session-1"))
                .andExpect(status().isOk());

        List<LoggedRequest> requests = wireMock.getAllServeEvents().stream()
                .map(event -> event.getRequest())
                .toList();
        assertThat(requests).hasSize(2);
        assertThat(requests).anySatisfy(request -> assertThat(request.getBodyAsString())
            .contains(OpenAiWireMockStubs.QUERY_ANALYSIS_MARKER));
        assertThat(requests).anySatisfy(request -> assertThat(request.getBodyAsString())
            .contains(OpenAiWireMockStubs.PRODUCT_FILTER_MARKER));
    }
}
