package com.ecomassistant.controller;

import com.ecomassistant.dto.ProductSearchRequestDto;
import com.ecomassistant.memory.ConversationLocks;
import com.ecomassistant.service.ProductSearchService;
import com.ecomassistant.service.SearchResult;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ecomassistant.dto.ChatResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
public class ProductsController {

    private static final Logger log = LoggerFactory.getLogger(ProductsController.class);
    private final ProductSearchService productSearchService;
    private final ChatMemory chatMemory;
    private final ConversationLocks conversationLocks = new ConversationLocks();

    public ProductsController(ProductSearchService productSearchService, ChatMemory chatMemory) {
        this.productSearchService = productSearchService;
        this.chatMemory = chatMemory;
    }

    @GetMapping("/search")
    @ResponseBody
    public ChatResponseDto findProducts(@Valid @ModelAttribute ProductSearchRequestDto searchRequest) {

        String message = searchRequest.getMessage();
        String conversationId = searchRequest.getConversationId();

        return conversationLocks.withLock(conversationId, () -> {
            if (searchRequest.isClearMemory()) {
                chatMemory.clear(conversationId);
                log.info("Conversation with id {} cleared", conversationId);
            }
            log.info("User message: {}", message);

            SearchResult searchResult = productSearchService.searchProducts(message, conversationId);
            return new ChatResponseDto(searchResult.products(), searchResult.interpretation(), null);
        });
    }
}
