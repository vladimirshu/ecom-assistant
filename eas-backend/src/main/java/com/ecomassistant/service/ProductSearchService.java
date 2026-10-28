package com.ecomassistant.service;

import com.ecomassistant.VectorStoreUtils;
import com.ecomassistant.config.EcomSearchProperties;
import com.ecomassistant.entity.Product;
import com.ecomassistant.exception.IllegalAssistantResponseException;
import com.ecomassistant.guardrails.SkuOutputGuardrail;
import com.ecomassistant.rag.ProductSelection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;

import java.util.List;

/**
 * Runs the single product-search pipeline: one call to {@code productSearchChatClient}
 * whose advisor chain performs query analysis (call #1), retrieval, and product filtering
 * (call #2). The retrieved documents are read back from the response metadata and the
 * deterministic {@link SkuOutputGuardrail} keeps only the selected SKUs, in retrieval
 * order.
 */
@Service
public class ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);

    private final ChatClient productSearchChatClient;
    private final SkuOutputGuardrail skuOutputGuardrail;
    private final EcomSearchProperties searchProperties;

    public ProductSearchService(@Qualifier("productSearchChatClient") ChatClient productSearchChatClient,
            SkuOutputGuardrail skuOutputGuardrail,
            EcomSearchProperties searchProperties) {
        this.productSearchChatClient = productSearchChatClient;
        this.skuOutputGuardrail = skuOutputGuardrail;
        this.searchProperties = searchProperties;
    }

    public List<Product> searchProducts(String message, String conversationId) {
        Prompt prompt = Prompt.builder().messages(UserMessage.builder().text(message).build()).build();
        try {
            ResponseEntity<ChatResponse, ProductSelection> response = productSearchChatClient
                .prompt(prompt)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .responseEntity(ProductSelection.class);

            List<Document> documents = retrievedDocuments(response.response());
            List<Document> selected = skuOutputGuardrail.apply(documents, response.entity(),
                    searchProperties.maxResults());

            List<Product> products = selected.stream().map(VectorStoreUtils::documentToProduct).toList();
            log.info("Returning {} products matching the search criteria", products.size());
            return products;
        }
        catch (JacksonException e) {
            throw new IllegalAssistantResponseException(
                    "Product filtering response does not match the expected schema", e.getMessage());
        }
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
