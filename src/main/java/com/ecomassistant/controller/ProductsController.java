package com.ecomassistant.controller;

import com.ecomassistant.dto.ProductSearchRequestDto;
import com.ecomassistant.entity.Product;
import com.ecomassistant.service.ProductSearchService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseBody;

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
    private final ProductSearchService productSearchService;
    private final ChatMemory chatMemory;

    public ProductsController(final ChatClient assistantChatClient,
                              ProductSearchService productSearchService,
                              ChatMemory chatMemory) {
        this.chatClient = assistantChatClient;
        this.productSearchService = productSearchService;
        this.chatMemory = chatMemory;
    }

    @GetMapping("/search")
    @ResponseBody
    public ChatResponseDto findProducts(@Valid @ModelAttribute ProductSearchRequestDto searchRequest) {

        String message = searchRequest.getMessage();
        String conversationId = searchRequest.getConversationId();

        if (searchRequest.isClearMemory()) {
            chatMemory.clear(conversationId);
            log.info("Conversation with id {} cleared", conversationId);
        }
        log.info("User message: {}", message);

        UserMessage userMessage = UserMessage.builder().text(message).build();
        Prompt prompt = Prompt.builder().messages(userMessage).build();
        AssistantToolsCallback assistantToolsCallback = new AssistantToolsCallback();
        AssistantMessage assistantMessage = chatClient.prompt(prompt)
                .toolContext(Map.of(AssistantToolsCallback.class.getName(), assistantToolsCallback))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call().chatResponse().getResult()
                .getOutput();
        String responseMessage = assistantMessage.getText();
        log.info("Assistant response: {}", responseMessage);

        if (!assistantToolsCallback.isValidPrompt()) {
            return new ChatResponseDto(null, assistantToolsCallback.getSuggestedPrompts());
        }

        List<Product> searchResults = productSearchService.searchProducts(responseMessage, assistantToolsCallback);
        return new ChatResponseDto(searchResults, null);

    }
}
