package com.ecomassistant.service;

import com.ecomassistant.VectorStoreUtils;
import com.ecomassistant.config.EcomSearchProperties;
import com.ecomassistant.dto.ProductDto;
import com.ecomassistant.dto.ProductDtoMapper;
import com.ecomassistant.dto.SearchInterpretationDto;
import com.ecomassistant.exception.IllegalAssistantResponseException;
import com.ecomassistant.guardrails.QueryAnalysis;
import com.ecomassistant.guardrails.SkuOutputGuardrail;
import com.ecomassistant.rag.ProductFilterContextKeys;
import com.ecomassistant.rag.ProductSelection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;

import java.util.List;

/**
 * Runs the single product-search pipeline: one call to {@code productSearchChatClient}
 * whose advisor chain performs query analysis (call #1), retrieval, and product filtering
 * (call #2). The retrieved documents are read back from the response metadata, the
 * deterministic {@link SkuOutputGuardrail} keeps only the selected SKUs in retrieval
 * order, and the validated {@link QueryAnalysis} stored in the response context is mapped
 * to the response interpretation.
 */
@Service
public class ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);

    private final ChatClient productSearchChatClient;
    private final SkuOutputGuardrail skuOutputGuardrail;
    private final EcomSearchProperties searchProperties;
    private final ProductDtoMapper productDtoMapper;

    public ProductSearchService(@Qualifier("productSearchChatClient") ChatClient productSearchChatClient,
            SkuOutputGuardrail skuOutputGuardrail,
            EcomSearchProperties searchProperties,
            ProductDtoMapper productDtoMapper) {
        this.productSearchChatClient = productSearchChatClient;
        this.skuOutputGuardrail = skuOutputGuardrail;
        this.searchProperties = searchProperties;
        this.productDtoMapper = productDtoMapper;
    }

    public SearchResult searchProducts(String message, String conversationId) {
        Prompt prompt = Prompt.builder().messages(UserMessage.builder().text(message).build()).build();
        try {
            ChatClientResponse chatClientResponse = productSearchChatClient
                .prompt(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .chatClientResponse();

            ChatResponse chatResponse = chatClientResponse.chatResponse();
            List<Document> documents = retrievedDocuments(chatResponse);
            ProductSelection selection = convertSelection(chatResponse);

            List<Document> selected = skuOutputGuardrail.apply(documents, selection,
                    searchProperties.maxResults());

            List<ProductDto> products = selected.stream()
                .map(VectorStoreUtils::documentToProduct)
                .map(productDtoMapper::toDto)
                .toList();

            SearchInterpretationDto interpretation = interpretation(chatClientResponse);

            log.info("Returning {} products matching the search criteria", products.size());
            return new SearchResult(products, interpretation);
        }
        catch (JacksonException e) {
            throw new IllegalAssistantResponseException(
                    "Product filtering response does not match the expected schema", e.getMessage());
        }
    }

    private static ProductSelection convertSelection(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null
                || chatResponse.getResult().getOutput() == null) {
            throw new IllegalAssistantResponseException(
                    "Product filtering response is missing content", "empty response");
        }
        String content = chatResponse.getResult().getOutput().getText();
        return new BeanOutputConverter<>(ProductSelection.class).convert(content);
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
