package com.ecomassistant.limits;

import com.ecomassistant.guardrails.QueryAnalysis;
import com.ecomassistant.retrieval.QdrantRecommendDocumentRetriever;
import com.ecomassistant.support.OpenAiWireMockStubs;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Daily-budget behaviour of {@code GET /api/v1/search}: with a budget of two searches a
 * third valid search is rejected with {@code DAILY_BUDGET_EXHAUSTED} and a
 * {@code Retry-After} until the next UTC midnight.
 */
class SearchDailyBudgetIntegrationTest extends AbstractSearchLimitIntegrationTest {

    @DynamicPropertySource
    static void lowBudget(DynamicPropertyRegistry registry) {
        registry.add("ecom.limits.daily-budget", () -> "2");
    }

    @MockitoBean
    private QdrantRecommendDocumentRetriever retriever;

    private void stubSuccessfulSearch() {
        OpenAiWireMockStubs.stubQueryAnalysis(wireMock,
                new QueryAnalysis(true, List.of(), List.of(), "rain jacket", List.of(), null, false));
        when(retriever.retrieve(any())).thenReturn(List.of());
    }

    private static MockHttpServletRequestBuilder search(String client, String message) {
        return get("/api/v1/search")
                .header(ClientKeyResolver.X_FORWARDED_FOR, client)
                .param("message", message)
                .param("conversationId", java.util.UUID.randomUUID().toString());
    }

    @Test
    void thirdSearchExhaustsDailyBudget() throws Exception {
        stubSuccessfulSearch();
        mockMvc.perform(search("203.0.113.9", "rain jacket")).andExpect(status().isOk());
        mockMvc.perform(search("203.0.113.9", "rain jacket")).andExpect(status().isOk());

        MvcResult result = mockMvc.perform(search("203.0.113.9", "rain jacket"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("DAILY_BUDGET_EXHAUSTED"))
                .andExpect(jsonPath("$.message").exists())
                .andReturn();

        long retryAfter = Long.parseLong(result.getResponse().getHeader("Retry-After"));
        assertThat(retryAfter).isBetween(1L, 24 * 3600L);
    }
}
