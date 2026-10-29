package com.ecomassistant.queryanalysis;

import com.ecomassistant.guardrails.QueryAnalysis;
import com.ecomassistant.rag.ProductSelection;
import com.ecomassistant.support.AbstractQueryAnalysisIntegrationTest;
import com.ecomassistant.support.OpenAiWireMockStubs;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Conversation memory behaviour of {@code GET /api/v1/search}, observed in the LLM request bodies.
 * The context is discarded afterwards because it is bound to this class's Qdrant container.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConversationMemoryIntegrationTest extends AbstractQueryAnalysisIntegrationTest {

    @Autowired
    private ChatMemory chatMemory;

    private static QueryAnalysis allowed(String searchQuery) {
        return new QueryAnalysis(true, List.of(), List.of(), searchQuery, List.of(), null, false);
    }

    private void stubAllowed(String searchQuery) {
        OpenAiWireMockStubs.stubQueryAnalysis(wireMock, allowed(searchQuery));
        OpenAiWireMockStubs.stubProductSelection(wireMock, new ProductSelection(List.of("RAIN1")));
    }

    private void search(String message, String conversationId, boolean clear, int expectedStatus) throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("message", message)
                        .param("conversationId", conversationId)
                        .param("clearMemory", String.valueOf(clear)))
                .andExpect(status().is(expectedStatus));
    }

    private void search(String message, String conversationId) throws Exception {
        search(message, conversationId, false, 200);
    }

    private List<String> bodies(String marker) {
        return wireMock.getAllServeEvents().stream()
                .sorted(java.util.Comparator.comparing(e -> e.getRequest().getLoggedDate()))
                .map(e -> e.getRequest().getBodyAsString())
                .filter(b -> b.contains(marker))
                .toList();
    }

    private List<String> analysisBodies() {
        return bodies(OpenAiWireMockStubs.QUERY_ANALYSIS_MARKER);
    }

    @Test
    void refinementSeesPreviousTurnAndFilteringDoesNot() throws Exception {
        OpenAiWireMockStubs.stubChatCompletionForMarkerSequence(wireMock,
                OpenAiWireMockStubs.QUERY_ANALYSIS_MARKER,
                OpenAiWireMockStubs.queryAnalysisJson(allowed("warm winter jacket marker-one")),
                OpenAiWireMockStubs.queryAnalysisJson(allowed("navy winter jacket marker-two")));
        OpenAiWireMockStubs.stubProductSelection(wireMock, new ProductSelection(List.of("RAIN1")));
        String id = UUID.randomUUID().toString();

        search("warm winter jacket", id);
        search("same but in navy", id);

        List<String> analysis = analysisBodies();
        assertThat(analysis).hasSize(2);
        String second = analysis.get(1);
        assertThat(second).contains("warm winter jacket", "marker-one", "same but in navy");
        assertThat(second.indexOf("warm winter jacket")).isLessThan(second.indexOf("same but in navy"));
        assertThat(analysis.get(0)).doesNotContain("same but in navy");
        List<String> filtering = bodies(OpenAiWireMockStubs.PRODUCT_FILTER_MARKER);
        assertThat(filtering).hasSize(2);
        assertThat(filtering.get(1)).contains("marker-two").doesNotContain("marker-one", "warm winter jacket");
    }

    @Test
    void conversationsAreIsolated() throws Exception {
        stubAllowed("leather jacket");

        search("leather jacket", "a-" + UUID.randomUUID());
        search("same but in navy", "b-" + UUID.randomUUID());

        assertThat(analysisBodies().get(1)).doesNotContain("leather jacket");
    }

    @Test
    void rejectedTurnIsRemembered() throws Exception {
        QueryAnalysis rejected = new QueryAnalysis(false, List.of("contradictory request marker-rejection"),
                List.of("a light summer jacket"), "", List.of(), null, false);
        OpenAiWireMockStubs.stubChatCompletionForMarkerSequence(wireMock,
                OpenAiWireMockStubs.QUERY_ANALYSIS_MARKER,
                OpenAiWireMockStubs.queryAnalysisJson(rejected),
                OpenAiWireMockStubs.queryAnalysisJson(allowed("light summer jacket")));
        OpenAiWireMockStubs.stubProductSelection(wireMock, new ProductSelection(List.of("RAIN1")));
        String id = UUID.randomUUID().toString();

        search("a winter jacket for summer", id, false, 422);
        search("a light summer jacket", id);

        assertThat(analysisBodies().get(1)).contains("a winter jacket for summer", "marker-rejection");
    }

    @Test
    void exhaustedSchemaRetriesLeaveHistoryUnchanged() throws Exception {
        String invalid = "{\"allowed\":\"not-a-boolean\"}";
        OpenAiWireMockStubs.stubChatCompletionForMarkerSequence(wireMock,
                OpenAiWireMockStubs.QUERY_ANALYSIS_MARKER,
                OpenAiWireMockStubs.queryAnalysisJson(allowed("original jacket")),
                invalid, invalid, invalid,
                OpenAiWireMockStubs.queryAnalysisJson(allowed("refined jacket")));
        OpenAiWireMockStubs.stubProductSelection(wireMock, new ProductSelection(List.of("RAIN1")));
        String id = UUID.randomUUID().toString();

        search("original jacket", id);
        List<Message> before = List.copyOf(chatMemory.get(id));
        search("failed request", id, false, 500);
        assertThat(chatMemory.get(id)).isEqualTo(before);
        search("refine jacket", id);

        List<String> requests = analysisBodies();
        String lastRequest = requests.get(requests.size() - 1);
        assertThat(lastRequest).contains("original jacket", "refine jacket")
                .doesNotContain("failed request", "not-a-boolean");
        assertThat(chatMemory.get(id)).hasSize(4);
    }

    @Test
    void successfulSchemaRetryStoresOnlyTheOriginalMessageAndValidResponse() throws Exception {
        OpenAiWireMockStubs.stubChatCompletionForMarkerSequence(wireMock,
                OpenAiWireMockStubs.QUERY_ANALYSIS_MARKER,
                "{\"allowed\":\"not-a-boolean\"}",
                OpenAiWireMockStubs.queryAnalysisJson(allowed("rain jacket")));
        OpenAiWireMockStubs.stubProductSelection(wireMock, new ProductSelection(List.of("RAIN1")));
        String id = UUID.randomUUID().toString();

        search("rain jacket", id);

        List<Message> history = chatMemory.get(id);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getText()).isEqualTo("rain jacket");
        assertThat(history.get(1).getText()).contains("rain jacket").doesNotContain("not-a-boolean");
    }

    @Test
    void clearMemoryResetsConversation() throws Exception {
        stubAllowed("rain jacket");
        String id = UUID.randomUUID().toString();

        search("first message", id);
        search("leather jacket", id, true, 200);

        assertThat(analysisBodies().get(1)).doesNotContain("first message");
        List<Message> history = chatMemory.get(id);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).getText()).isEqualTo("leather jacket");
    }

    @Test
    void clearMemoryDoesNotTouchOtherConversations() throws Exception {
        stubAllowed("rain jacket");
        String a = "a-" + UUID.randomUUID();
        String b = "b-" + UUID.randomUUID();
        search("message of a", a);
        search("message of b", b);
        List<Message> beforeB = List.copyOf(chatMemory.get(b));

        search("fresh message", a, true, 200);

        assertThat(chatMemory.get(b)).isEqualTo(beforeB);
        search("another for b", b);
        List<String> all = analysisBodies();
        assertThat(all.get(all.size() - 1)).contains("message of b");
    }
}
