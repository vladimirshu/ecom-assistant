package com.ecomassistant.service;

import com.ecomassistant.config.MetricsConfig;
import com.ecomassistant.dto.ProductDtoMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductSearchTimingTest {

    @Test
    void recordsSuccessfulAndFailedCalls() {
        try (var context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            var client = context.getBean(ChatClient.class);
            var registry = context.getBean(MeterRegistry.class);
            var service = context.getBean(ProductSearchService.class);
            var response = mock(ChatClientResponse.class);
            var chatResponse = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
            when(chatResponse.getResult().getOutput().getText()).thenReturn("{\"skus\":[]}");
            when(response.chatResponse()).thenReturn(chatResponse);
            when(response.context()).thenReturn(Map.of());
            when(client.prompt(any(org.springframework.ai.chat.prompt.Prompt.class))
                    .advisors(any(java.util.function.Consumer.class)).call().chatClientResponse())
                    .thenReturn(response).thenThrow(new IllegalStateException("search failed"));

            assertThat(service.searchProducts("shoes", "conversation").products()).isEmpty();
            assertThatThrownBy(() -> service.searchProducts("shoes", "conversation"))
                    .isInstanceOf(IllegalStateException.class).hasMessage("search failed");

            assertThat(registry.get("product.search").tag("exception", "none").timer().count()).isEqualTo(1);
            assertThat(registry.get("product.search").tag("exception", "IllegalStateException").timer().count())
                    .isEqualTo(1);
        }
    }

    @Configuration
    @EnableAspectJAutoProxy
    @Import(MetricsConfig.class)
    static class TestConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        ChatClient chatClient() {
            return mock(ChatClient.class, RETURNS_DEEP_STUBS);
        }

        @Bean
        ProductSearchService productSearchService(ChatClient chatClient) {
            return new ProductSearchService(chatClient, mock(ProductDtoMapper.class));
        }
    }
}