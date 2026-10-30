package com.ecomassistant.controller;

import com.ecomassistant.dto.ProductSearchRequestDto;
import com.ecomassistant.limits.ClientKeyResolver;
import com.ecomassistant.limits.SearchAdmission;
import com.ecomassistant.listing.CatalogListingService;
import com.ecomassistant.service.ProductSearchService;
import com.ecomassistant.service.SearchResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ProductsControllerConcurrencyTest {
    @Test
    void resetWaitsForEarlierSearchButOtherConversationsCanProceed() throws Exception {
        ProductSearchService service = mock(ProductSearchService.class);
        ChatMemory memory = mock(ChatMemory.class);
        SearchAdmission searchAdmission = mock(SearchAdmission.class);
        ClientKeyResolver clientKeyResolver = mock(ClientKeyResolver.class);
        ProductsController controller = new ProductsController(service, memory, searchAdmission, clientKeyResolver,
                mock(CatalogListingService.class));
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch finishFirst = new CountDownLatch(1);
        SearchResult result = new SearchResult(List.of(), null);
        when(service.searchProducts("first search", "a")).thenAnswer(invocation -> {
            firstStarted.countDown();
            if (!finishFirst.await(5, TimeUnit.SECONDS)) throw new AssertionError("first search timed out");
            return result;
        });
        when(service.searchProducts("reset search", "a")).thenReturn(result);
        when(service.searchProducts("other search", "b")).thenReturn(result);
        var pool = Executors.newFixedThreadPool(3);
        try {
            var first = pool.submit(() -> controller.findProducts(new ProductSearchRequestDto("first search", "a", false), null));
            assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
            CountDownLatch resetStarted = new CountDownLatch(1);
            var reset = pool.submit(() -> {
                resetStarted.countDown();
                return controller.findProducts(new ProductSearchRequestDto("reset search", "a", true), null);
            });
            assertThat(resetStarted.await(5, TimeUnit.SECONDS)).isTrue();
            var other = pool.submit(() -> controller.findProducts(new ProductSearchRequestDto("other search", "b", false), null));
            assertThat(other.get(5, TimeUnit.SECONDS)).isNotNull();
            assertThatThrownBy(() -> reset.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            verify(memory, never()).clear("a");

            finishFirst.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isNotNull();
            assertThat(reset.get(5, TimeUnit.SECONDS)).isNotNull();
            var order = inOrder(memory, service);
            order.verify(service).searchProducts("first search", "a");
            order.verify(memory).clear("a");
            order.verify(service).searchProducts("reset search", "a");
        } finally {
            finishFirst.countDown();
            pool.shutdownNow();
        }
    }
}
