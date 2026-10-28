package com.ecomassistant.guardrails;

import com.ecomassistant.exception.IllegalAssistantResponseException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

/**
 * Runs the single query-analysis LLM call and returns its structured {@link QueryAnalysis}.
 *
 * <p>The query-analysis client retries schema-invalid responses and commits a turn only
 * when the final response is schema-valid and convertible. Semantic validation is performed
 * separately by {@link QueryAnalysisValidator}, after the turn has been stored.
 */
@Component
public class QueryAnalyzer {

    private final ChatClient queryAnalysisChatClient;

    public QueryAnalyzer(@Qualifier("queryAnalysisChatClient") ChatClient queryAnalysisChatClient) {
        this.queryAnalysisChatClient = queryAnalysisChatClient;
    }

    public QueryAnalysis analyze(String message, String conversationId) {
        UserMessage userMessage = UserMessage.builder().text(message).build();
        Prompt prompt = Prompt.builder().messages(userMessage).build();
        try {
            return queryAnalysisChatClient.prompt(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .entity(QueryAnalysis.class);
        } catch (JacksonException e) {
            throw new IllegalAssistantResponseException(
                    "Query analysis response does not match the expected schema", e.getMessage());
        }
    }
}
