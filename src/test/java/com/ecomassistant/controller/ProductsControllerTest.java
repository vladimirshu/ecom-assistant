package com.ecomassistant.controller;

import com.ecomassistant.service.ProductSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductsController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatClient chatClient;

    @MockitoBean
    private ProductSearchService productSearchService;

    @MockitoBean
    private ChatMemory chatMemory;

    @Test
    void angleBracketsReturnBadRequestWithoutInvokingChatClient() throws Exception {
        mockMvc.perform(get("/search")
                        .param("message", "jacket <script>")
                        .param("conversationId", "session_1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verifyNoInteractions(chatClient);
    }

    @Test
    void tooShortMessageReturnsBadRequestWithoutInvokingChatClient() throws Exception {
        mockMvc.perform(get("/search")
                        .param("message", "  a  ")
                        .param("conversationId", "session_1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verifyNoInteractions(chatClient);
    }

    @Test
    void invalidConversationIdReturnsBadRequestWithoutInvokingChatClient() throws Exception {
        mockMvc.perform(get("/search")
                        .param("message", "blue jacket")
                        .param("conversationId", "abc;drop"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.conversationId").exists());

        verifyNoInteractions(chatClient);
    }
}
