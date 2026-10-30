package com.ecomassistant.controller;

import com.ecomassistant.limits.ClientKeyResolver;
import com.ecomassistant.limits.SearchAdmission;
import com.ecomassistant.listing.CatalogListingService;
import com.ecomassistant.listing.CatalogPage;
import com.ecomassistant.listing.CatalogRequestException;
import com.ecomassistant.listing.CatalogUnavailableException;
import com.ecomassistant.service.ProductSearchService;
import com.ecomassistant.service.SearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductsController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductSearchService productSearchService;

    @MockitoBean
    private ChatMemory chatMemory;

    @MockitoBean
    private SearchAdmission searchAdmission;

    @MockitoBean
    private ClientKeyResolver clientKeyResolver;

    @MockitoBean
    private CatalogListingService catalogListingService;

    @Test
    void angleBracketsReturnBadRequestWithoutInvokingSearch() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("message", "jacket <script>")
                        .param("conversationId", "session_1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verifyNoInteractions(productSearchService);
    }

    @Test
    void tooShortMessageReturnsBadRequestWithoutInvokingSearch() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("message", "  a  ")
                        .param("conversationId", "session_1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verifyNoInteractions(productSearchService);
    }

    @Test
    void invalidConversationIdReturnsBadRequestWithoutInvokingSearch() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("message", "blue jacket")
                        .param("conversationId", "abc;drop"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.conversationId").exists());

        verifyNoInteractions(productSearchService);
    }

    @Test
    void missingConversationIdReturnsBadRequestWithoutInvokingSearch() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("message", "rain jacket"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.conversationId").exists());

        verifyNoInteractions(productSearchService);
    }

    @Test
    void emptyConversationIdReturnsBadRequestWithoutInvokingSearch() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("message", "rain jacket")
                        .param("conversationId", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.conversationId").exists());

        verifyNoInteractions(productSearchService);
    }

    @Test
    void uuidConversationIdIsAccepted() throws Exception {
        String uuid = "3f2b9c1e-7a4d-4e8f-9b21-5c6d7e8f9a0b";
        when(productSearchService.searchProducts("rain jacket", uuid))
                .thenReturn(new SearchResult(List.of(), null));

        mockMvc.perform(get("/api/v1/search")
                        .param("message", "rain jacket")
                        .param("conversationId", uuid))
                .andExpect(status().isOk());

        verify(productSearchService).searchProducts("rain jacket", uuid);
    }

    @Test
    void oldSearchPathReturnsNotFoundWithoutInvokingSearch() throws Exception {
        mockMvc.perform(get("/search")
                        .param("message", "blue jacket")
                        .param("conversationId", "session_1"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(productSearchService);
        verifyNoInteractions(chatMemory);
    }

    @Test
    void catalogListingReturnsProductsAndCursor() throws Exception {
        when(catalogListingService.list(null, null, null))
                .thenReturn(new CatalogPage(List.of(), "opaque-cursor"));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray())
                .andExpect(jsonPath("$.nextCursor").value("opaque-cursor"));

        verify(catalogListingService).list(null, null, null);
        verifyNoInteractions(searchAdmission);
    }

    @Test
    void catalogListingValidationErrorReturns400WithError() throws Exception {
        when(catalogListingService.list("unknown-branch", null, null))
                .thenThrow(new CatalogRequestException("Unknown category"));

        mockMvc.perform(get("/api/v1/products").param("category", "unknown-branch"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown category"));
    }

    @Test
    void catalogListingUnavailableReturns503WithError() throws Exception {
        when(catalogListingService.list(null, null, null))
                .thenThrow(new CatalogUnavailableException("Catalog is not ready"));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Catalog is not ready"));
    }
}
