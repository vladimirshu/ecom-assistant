package com.ecomassistant.support;

import com.ecomassistant.guardrails.QueryAnalysis;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WireMock stubs for the OpenAI chat-completions endpoint. Each stub returns an OpenAI
 * chat-completion response whose {@code choices[0].message.content} is the provided JSON
 * string (typically a {@link QueryAnalysis} serialized to JSON).
 */
public final class OpenAiWireMockStubs {

    /** Matches both {@code /chat/completions} and {@code /v1/chat/completions}. */
    public static final String CHAT_COMPLETIONS_PATH_PATTERN = ".*/chat/completions";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private OpenAiWireMockStubs() {
    }

    public static void stubChatCompletion(WireMockServer server, QueryAnalysis analysis) {
        stubChatCompletionContent(server, queryAnalysisJson(analysis));
    }

    /** Stubs a single response whose message content is the given raw JSON string. */
    public static void stubChatCompletionContent(WireMockServer server, String content) {
        server.stubFor(WireMock.post(WireMock.urlPathMatching(CHAT_COMPLETIONS_PATH_PATTERN))
            .willReturn(WireMock.aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(chatCompletionResponse(content))));
    }

    /** Stubs a sequence of responses, one per successive request, using a scenario. */
    public static void stubChatCompletionSequence(WireMockServer server, String... contents) {
        String scenario = "chat-completion-sequence";
        for (int i = 0; i < contents.length; i++) {
            String currentState = i == 0 ? Scenario.STARTED : "step-" + i;
            String nextState = i == contents.length - 1 ? Scenario.STARTED : "step-" + (i + 1);
            server.stubFor(WireMock.post(WireMock.urlPathMatching(CHAT_COMPLETIONS_PATH_PATTERN))
                .inScenario(scenario)
                .whenScenarioStateIs(currentState)
                .willReturn(WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(chatCompletionResponse(contents[i])))
                .willSetStateTo(nextState));
        }
    }

    /** Serializes a {@link QueryAnalysis} to JSON, omitting a null price constraint. */
    public static String queryAnalysisJson(QueryAnalysis analysis) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("allowed", analysis.allowed());
        map.put("rejectionReasons", analysis.rejectionReasons());
        map.put("suggestedPrompts", analysis.suggestedPrompts());
        map.put("searchQuery", analysis.searchQuery());
        map.put("negativeExamples", analysis.negativeExamples());
        if (analysis.priceConstraint() != null) {
            map.put("priceConstraint", Map.of(
                    "operator", analysis.priceConstraint().operator().name(),
                    "value", analysis.priceConstraint().value()));
        }
        map.put("sortByLowestPrice", analysis.sortByLowestPrice());
        return write(map);
    }

    private static String chatCompletionResponse(String content) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", "chatcmpl-test");
        body.put("object", "chat.completion");
        body.put("created", 1700000000L);
        body.put("model", "gpt-4o-mini");
        body.put("choices", List.of(Map.of(
                "index", 0,
                "message", Map.of("role", "assistant", "content", content),
                "finish_reason", "stop")));
        body.put("usage", Map.of(
                "prompt_tokens", 10,
                "completion_tokens", 20,
                "total_tokens", 30));
        return write(body);
    }

    private static String write(Object value) {
        try {
            return JSON_MAPPER.writeValueAsString(value);
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to serialize " + value, e);
        }
    }
}
