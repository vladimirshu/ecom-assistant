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
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Skips the product-filtering LLM call when retrieval returned no candidates, returning a
 * synthetic {@code {"skus":[]}} response instead. This is placed inside (after) the
 * {@link RetrievalAugmentationAdvisor}, whose {@code before()} populates
 * {@link RetrievalAugmentationAdvisor#DOCUMENT_CONTEXT} in the request context.
 */
@Component
public class EmptyRetrievalAdvisor implements CallAdvisor, StreamAdvisor {

    private static final int ORDER = -200;

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        if (isEmptyRetrieval(chatClientRequest)) {
            return emptyResponse(chatClientRequest);
        }
        return callAdvisorChain.nextCall(chatClientRequest);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
            StreamAdvisorChain streamAdvisorChain) {
        if (isEmptyRetrieval(chatClientRequest)) {
            return Flux.just(emptyResponse(chatClientRequest));
        }
        return streamAdvisorChain.nextStream(chatClientRequest);
    }

    private static boolean isEmptyRetrieval(ChatClientRequest chatClientRequest) {
        Object value = chatClientRequest.context().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        return !(value instanceof List<?> list) || list.isEmpty();
    }

    private static ChatClientResponse emptyResponse(ChatClientRequest chatClientRequest) {
        return ChatClientResponse.builder()
            .chatResponse(ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage("{\"skus\":[]}"))))
                .build())
            .context(Map.copyOf(chatClientRequest.context()))
            .build();
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
