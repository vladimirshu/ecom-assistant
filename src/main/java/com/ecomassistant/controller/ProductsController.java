package com.ecomassistant.controller;

import com.ecomassistant.dto.ProductSearchRequestDto;
import com.ecomassistant.entity.Product;
import com.ecomassistant.service.InputSanitizationService;
import com.ecomassistant.service.ProductSearchService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

@RestController
public class ProductsController {

    private static final Logger log = LoggerFactory.getLogger(ProductsController.class);
    private final ChatClient chatClient;
    private final ProductRepository productRepository;
    private final ProductSearchService productSearchService;
    private final ChatMemory chatMemory;
    private final InputSanitizationService inputSanitizationService;

    public ProductsController(final ChatClient assistantChatClient,
                              ProductRepository productRepository,
                              ProductSearchService productSearchService,
                              ChatMemory chatMemory,
                              InputSanitizationService inputSanitizationService) {
        this.chatClient = assistantChatClient;
        this.productRepository = productRepository;
        this.productSearchService = productSearchService;
        this.chatMemory = chatMemory;
        this.inputSanitizationService = inputSanitizationService;
    }

    @GetMapping("/search")
    @ResponseBody
    public ChatResponseDto findProducts(@Valid @ModelAttribute ProductSearchRequestDto searchRequest) {

        // Sanitize inputs
        String sanitizedMessage = inputSanitizationService.sanitizeInput(searchRequest.getMessage());
        String sanitizedConversationId = inputSanitizationService.sanitizeInput(searchRequest.getConversationId());

        if (searchRequest.isClearMemory()) {
            chatMemory.clear(sanitizedConversationId);
            log.info("Conversation with id {} cleared", sanitizedConversationId);
        }
        log.info("User message: {}", sanitizedMessage);

        UserMessage userMessage = UserMessage.builder().text(sanitizedMessage).build();
        Prompt prompt = Prompt.builder().messages(userMessage).build();
        AssistantToolsCallback assistantToolsCallback = new AssistantToolsCallback();
        AssistantMessage assistantMessage = chatClient.prompt(prompt)
                .toolContext(Map.of(AssistantToolsCallback.class.getName(), assistantToolsCallback))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sanitizedConversationId))
                .call().chatResponse().getResult()
                .getOutput();
        String responseMessage = assistantMessage.getText();
        log.info("Assistant response: {}", responseMessage);

        List<Product> filteredProducts = null;
        if (!assistantToolsCallback.isValidPrompt()) {
            return new ChatResponseDto(null, assistantToolsCallback.getSuggestedPrompts());
        }

        if (assistantToolsCallback.isFilterByPrice()) {
            BigDecimal price = new BigDecimal(assistantToolsCallback.getFilterProductsByPriceValue());
            String operator = assistantToolsCallback.getFilterProductsByPriceOperator().name();
            filteredProducts = productRepository.findByPrice(price, operator);
        }
        String sortParameter = assistantToolsCallback.isSortByPriceAsc() ? "asc" : null;
        var searchResults = productSearchService.searchProducts(responseMessage, assistantToolsCallback.getNegativeExamples(), filteredProducts, sortParameter);
        return new ChatResponseDto(searchResults, null);

    }
}