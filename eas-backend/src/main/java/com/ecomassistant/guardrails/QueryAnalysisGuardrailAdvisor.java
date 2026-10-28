package com.ecomassistant.guardrails;

import com.ecomassistant.rag.ProductFilterContextKeys;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Outermost advisor of the {@code productSearchChatClient}. Runs the single query-analysis
 * LLM call (call #1), validates its result and either rejects the request (throwing
 * {@link GuardrailRejectedException}) or rewrites the request for retrieval: the user
 * message is replaced with the English {@code searchQuery}, and the retrieval parameters
 * plus the customer's original message are added to the request context.
 */
@Component
public class QueryAnalysisGuardrailAdvisor implements BaseAdvisor {

    private static final int ORDER = -400;

    private final QueryAnalyzer queryAnalyzer;
    private final QueryAnalysisValidator queryAnalysisValidator;
    private final RetrievalParametersMapper retrievalParametersMapper;

    public QueryAnalysisGuardrailAdvisor(QueryAnalyzer queryAnalyzer,
            QueryAnalysisValidator queryAnalysisValidator,
            RetrievalParametersMapper retrievalParametersMapper) {
        this.queryAnalyzer = queryAnalyzer;
        this.queryAnalysisValidator = queryAnalysisValidator;
        this.retrievalParametersMapper = retrievalParametersMapper;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        String conversationId = String.valueOf(chatClientRequest.context().get(ChatMemory.CONVERSATION_ID));
        String message = chatClientRequest.prompt().getUserMessage().getText();

        QueryAnalysis analysis = queryAnalyzer.analyze(message, conversationId);
        queryAnalysisValidator.validate(analysis);

        Map<String, Object> context = new HashMap<>(chatClientRequest.context());
        context.putAll(retrievalParametersMapper.toContext(analysis));
        context.put(ProductFilterContextKeys.ORIGINAL_MESSAGE, message);
        context.put(ProductFilterContextKeys.QUERY_ANALYSIS, analysis);
        if (analysis.priceConstraint() != null) {
            context.put(ProductFilterContextKeys.PRICE_CONSTRAINT, analysis.priceConstraint());
        }

        return chatClientRequest.mutate()
            .prompt(chatClientRequest.prompt().augmentUserMessage(analysis.searchQuery()))
            .context(context)
            .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
