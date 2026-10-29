package com.ecomassistant.controller;

import com.ecomassistant.dto.ProductSearchRequestDto;
import com.ecomassistant.limits.ClientKeyResolver;
import com.ecomassistant.limits.SearchAdmission;
import com.ecomassistant.memory.ConversationLocks;
import com.ecomassistant.service.ProductSearchService;
import com.ecomassistant.service.SearchResult;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ecomassistant.dto.ChatResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class ProductsController {

    private static final Logger log = LoggerFactory.getLogger(ProductsController.class);
    private final ProductSearchService productSearchService;
    private final ChatMemory chatMemory;
    private final SearchAdmission searchAdmission;
    private final ClientKeyResolver clientKeyResolver;
    private final ConversationLocks conversationLocks = new ConversationLocks();

    public ProductsController(ProductSearchService productSearchService, ChatMemory chatMemory,
            SearchAdmission searchAdmission, ClientKeyResolver clientKeyResolver) {
        this.productSearchService = productSearchService;
        this.chatMemory = chatMemory;
        this.searchAdmission = searchAdmission;
        this.clientKeyResolver = clientKeyResolver;
    }

    @GetMapping("/search")
    @ResponseBody
    public ChatResponseDto findProducts(@Valid @ModelAttribute ProductSearchRequestDto searchRequest,
            HttpServletRequest request) {

        String message = searchRequest.getMessage();
        String conversationId = searchRequest.getConversationId();

        // After @Valid binding (a 400 costs nothing), before any LLM/embedding/vector call.
        searchAdmission.admit(clientKeyResolver.resolve(request));

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
