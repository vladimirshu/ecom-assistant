package com.ecomassistant.limits;

import com.ecomassistant.guardrails.QueryAnalysis;
import com.ecomassistant.retrieval.FakeEmbeddingModel;
import com.ecomassistant.retrieval.QdrantRecommendDocumentRetriever;
import com.ecomassistant.support.OpenAiWireMockStubs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rate-limit behaviour of {@code GET /api/v1/search}: a sixth search within a minute is a
 * 429 with {@code RATE_LIMITED} and a {@code Retry-After}; invalid requests consume no
 * quota; a rejected request makes no LLM, embedding or vector call; health is not limited.
 */
class SearchRateLimitingIntegrationTest extends AbstractSearchLimitIntegrationTest {

    @MockitoBean
    private QdrantRecommendDocumentRetriever retriever;

    @Autowired
    private FakeEmbeddingModel fakeEmbeddingModel;

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
    void sixthSearchInAMinuteReturns429RateLimited() throws Exception {
        stubSuccessfulSearch();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(search("203.0.113.1", "rain jacket")).andExpect(status().isOk());
        }

        MvcResult result = mockMvc.perform(search("203.0.113.1", "rain jacket"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message").exists())
                .andReturn();

        long retryAfter = Long.parseLong(result.getResponse().getHeader("Retry-After"));
        assertThat(retryAfter).isBetween(1L, 60L);
    }

    @Test
    void invalidRequestsConsumeNoQuota() throws Exception {
        stubSuccessfulSearch();
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(search("203.0.113.2", "jacket <script>"))
                    .andExpect(status().isBadRequest());
        }

        // Had the invalid requests consumed quota, the minute bucket would already be empty.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(search("203.0.113.2", "rain jacket")).andExpect(status().isOk());
        }
    }

    @Test
    void rejectedRequestMakesNoDownstreamCalls() throws Exception {
        stubSuccessfulSearch();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(search("203.0.113.3", "rain jacket")).andExpect(status().isOk());
        }
        int serveEventsBefore = wireMock.getAllServeEvents().size();
        long embedBefore = fakeEmbeddingModel.getEmbedCount();

        mockMvc.perform(search("203.0.113.3", "rain jacket"))
                .andExpect(status().isTooManyRequests());

        assertThat(wireMock.getAllServeEvents()).hasSize(serveEventsBefore);
        assertThat(fakeEmbeddingModel.getEmbedCount()).isEqualTo(embedBefore);
        verify(retriever, times(5)).retrieve(any());
    }

    @Test
    void healthEndpointIsNotLimited() throws Exception {
        stubSuccessfulSearch();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(search("203.0.113.4", "rain jacket")).andExpect(status().isOk());
        }
        mockMvc.perform(search("203.0.113.4", "rain jacket"))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }
}
