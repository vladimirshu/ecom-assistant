package com.ecomassistant.controller;

import com.ecomassistant.entity.Product;
import com.ecomassistant.service.ProductSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;

import com.ecomassistant.repository.ProductRepository;
import com.ecomassistant.dto.ChatResponseDto;
import com.ecomassistant.tools.AssistantToolsCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ProductsController {

    private static final Logger log = LoggerFactory.getLogger(ProductsController.class);
    private final ChatClient chatClient;
    private final ProductRepository productRepository;
    private final ProductSearchService productSearchService;

    public ProductsController(final ChatClient assistantChatClient,
                              ProductRepository productRepository,
                              ProductSearchService productSearchService) {
        this.chatClient = assistantChatClient;
        this.productRepository = productRepository;
        this.productSearchService = productSearchService;
    }

    @GetMapping("/search")
    @ResponseBody
    public ChatResponseDto findProducts(
            @RequestParam(value = "message", defaultValue = "I need a winter jacket that I can wear in summer as well (below 200 euro)") String message,
            @RequestParam(value = "conversationId", defaultValue = "1") String conversationId) {
        UserMessage userMessage = UserMessage.builder().text(message).build();
        Prompt prompt = Prompt.builder().messages(userMessage).build();
        AssistantToolsCallback assistantToolsCallback = new AssistantToolsCallback();
        chatClient.prompt(prompt)
                .toolContext(Map.of(AssistantToolsCallback.class.getName(), assistantToolsCallback))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId)).call().chatResponse();


        if (!assistantToolsCallback.isValidPrompt()) {
            return new ChatResponseDto(null, assistantToolsCallback.getSuggestedPrompt());
        } else if (assistantToolsCallback.isFilterByPrice()) {
            BigDecimal price = new BigDecimal(assistantToolsCallback.getFilterProductsByPriceValue());
            String operator = assistantToolsCallback.getFilterProductsByPriceOperator().name();
            List<Product> filteredProducts = productRepository.findByPrice(price, operator);
            List<Product> searchResults;
            if (assistantToolsCallback.isSortByPriceAsc()) {
                searchResults = productSearchService.searchProducts(message, filteredProducts, "asc");
            } else {
                searchResults = productSearchService.searchWithinProducts(message, filteredProducts);
            }
            return new ChatResponseDto(searchResults, null);
        } else {
            String sortParameter = assistantToolsCallback.isSortByPriceAsc() ? "asc" : null;
            List<Product> searchResults = productSearchService.searchProductsWithSort(message, sortParameter);
            return new ChatResponseDto(searchResults, null);
        }
    }
}