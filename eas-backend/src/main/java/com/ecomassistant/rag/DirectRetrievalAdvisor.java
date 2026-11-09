package com.ecomassistant.rag;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Short-circuits the product-search pipeline right after retrieval, returning the
 * retrieved documents directly without invoking the model. This replaces the former
 * product-filtering LLM call, so a search performs exactly one model round-trip (the
 * query-analysis call made by the outermost guardrail advisor).
 *
 * <p>This advisor is placed inside (after) the {@link RetrievalAugmentationAdvisor},
 * whose {@code before()} stores the retrieved documents under
 * {@link RetrievalAugmentationAdvisor#DOCUMENT_CONTEXT} in the request context. The
 * documents are copied into the response metadata so the search service can read them
 * back, and the request context (including the validated query analysis) is preserved so
 * the response interpretation can be built from it.
 */
@Component
public class DirectRetrievalAdvisor implements CallAdvisor, StreamAdvisor {

    private static final int ORDER = -200;

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        return directResponse(chatClientRequest);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
            StreamAdvisorChain streamAdvisorChain) {
        return Flux.just(directResponse(chatClientRequest));
    }

    private static ChatClientResponse directResponse(ChatClientRequest chatClientRequest) {
        List<Document> documents = documents(chatClientRequest);
        ChatResponse chatResponse = ChatResponse.builder()
            .generations(List.of(new Generation(new AssistantMessage(""))))
            .metadata(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, documents)
            .build();
        return ChatClientResponse.builder()
            .chatResponse(chatResponse)
            .context(Map.copyOf(chatClientRequest.context()))
            .build();
    }

    @SuppressWarnings("unchecked")
    private static List<Document> documents(ChatClientRequest chatClientRequest) {
        Object value = chatClientRequest.context().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        return value instanceof List<?> list ? (List<Document>) list : List.of();
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
