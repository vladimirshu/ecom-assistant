package com.ecomassistant.service;

import com.ecomassistant.VectorStoreUtils;
import com.ecomassistant.dto.ProductDto;
import com.ecomassistant.dto.ProductDtoMapper;
import com.ecomassistant.dto.SearchInterpretationDto;
import com.ecomassistant.guardrails.QueryAnalysis;
import com.ecomassistant.rag.ProductFilterContextKeys;
import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Runs the product-search pipeline through a single call to
 * {@code productSearchChatClient}, whose advisor chain performs query analysis (the only
 * LLM call), retrieval, and a direct-retrieval short-circuit that returns the retrieved
 * documents without a second model call. The retrieved documents are read back from the
 * response metadata, mapped to products, and the validated {@link QueryAnalysis} stored in
 * the response context is mapped to the response interpretation.
 */
@Service
public class ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);

    private final ChatClient productSearchChatClient;
    private final ProductDtoMapper productDtoMapper;

    public ProductSearchService(@Qualifier("productSearchChatClient") ChatClient productSearchChatClient,
            ProductDtoMapper productDtoMapper) {
        this.productSearchChatClient = productSearchChatClient;
        this.productDtoMapper = productDtoMapper;
    }

    @Timed(value = "product.search", description = "Product search latency")
    public SearchResult searchProducts(String message, String conversationId) {
        long startTime = System.currentTimeMillis();
        Prompt prompt = Prompt.builder().messages(UserMessage.builder().text(message).build()).build();
        ChatClientResponse chatClientResponse = productSearchChatClient
            .prompt(prompt)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
            .call()
            .chatClientResponse();

        ChatResponse chatResponse = chatClientResponse.chatResponse();
        List<Document> documents = retrievedDocuments(chatResponse);

        List<ProductDto> products = documents.stream()
            .map(VectorStoreUtils::documentToProduct)
            .map(productDtoMapper::toDto)
            .toList();

        SearchInterpretationDto interpretation = interpretation(chatClientResponse);

        log.info("Returning {} products matching the search criteria", products.size());
        log.info("Product search completed in {} ms", (System.currentTimeMillis() - startTime));
        return new SearchResult(products, interpretation);
    }

    private static SearchInterpretationDto interpretation(ChatClientResponse chatClientResponse) {
        Object value = chatClientResponse.context().get(ProductFilterContextKeys.QUERY_ANALYSIS);
        return value instanceof QueryAnalysis analysis ? SearchInterpretationDto.from(analysis) : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Document> retrievedDocuments(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return List.of();
        }
        Object value = chatResponse.getMetadata().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        return value instanceof List<?> list ? (List<Document>) list : List.of();
    }
}
