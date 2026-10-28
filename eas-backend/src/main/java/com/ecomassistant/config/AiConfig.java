package com.ecomassistant.config;

import com.ecomassistant.guardrails.QueryAnalysis;
import com.ecomassistant.guardrails.QueryAnalysisGuardrailAdvisor;
import com.ecomassistant.rag.EmptyRetrievalAdvisor;
import com.ecomassistant.rag.ProductSelection;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

@Configuration
public class AiConfig {
    public static final String REASONING_MODEL = "gpt-4o-mini";
    public static final String EMBEDDING_MODEL = "nomic-embed-text:latest";
    public static final int MAX_MESSAGES = 20;
    private static final double TEMPERATURE = 0.1;
    private static final double FREQUENCY_PENALTY = 1.0;
    private static final double PRESENCE_PENALTY = 1.0;

    public static final double TOP_P = 0.8;

    @Bean
    public ChatMemoryRepository chatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder().maxMessages(MAX_MESSAGES).chatMemoryRepository(chatMemoryRepository).build();
    }

    @Bean
    public SimpleLoggerAdvisor loggingAdvisor() {
        return new SimpleLoggerAdvisor(ChatClientRequest::toString, ChatResponse::toString, 0);
    }

    @Bean
    public StructuredOutputValidationAdvisor queryAnalysisSchemaValidationAdvisor() {
        return StructuredOutputValidationAdvisor.builder()
            .outputType(QueryAnalysis.class)
            .maxRepeatAttempts(2)
            .build();
    }

    @Bean
    public ChatClient queryAnalysisChatClient(ChatModel chatModel, ChatMemory chatMemory) {
        ToolCallingChatOptions.Builder chatOptionsBuilder = ToolCallingChatOptions.builder()
                .model(REASONING_MODEL)
                .temperature(TEMPERATURE)
                .topP(TOP_P)
                .frequencyPenalty(FREQUENCY_PENALTY)
                .presencePenalty(PRESENCE_PENALTY);
        return ChatClient.builder(chatModel)
                .defaultOptions(chatOptionsBuilder)
                .defaultSystem(loadSystemInstructions())
                .defaultAdvisors(loggingAdvisor(),
                        queryAnalysisSchemaValidationAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean
    public StructuredOutputValidationAdvisor productSelectionSchemaValidationAdvisor() {
        return StructuredOutputValidationAdvisor.builder()
            .outputType(ProductSelection.class)
            .maxRepeatAttempts(2)
            .advisorOrder(-100)
            .build();
    }

    /**
     * The single chat client backing the search endpoint. Its advisor chain (outer to
     * inner) runs: query-analysis guardrail (call #1), retrieval augmentation, the
     * empty-retrieval short-circuit, product-selection schema validation (call #2 with
     * retries) and logging. No memory advisor: the selection does not need history, which
     * call #1 already compressed into {@code searchQuery}.
     */
    @Bean
    public ChatClient productSearchChatClient(ChatModel chatModel,
            QueryAnalysisGuardrailAdvisor queryAnalysisGuardrailAdvisor,
            RetrievalAugmentationAdvisor productSearchRetrievalAdvisor,
            EmptyRetrievalAdvisor emptyRetrievalAdvisor,
            @Qualifier("productSelectionSchemaValidationAdvisor") StructuredOutputValidationAdvisor productSelectionSchemaValidationAdvisor,
            SimpleLoggerAdvisor loggingAdvisor) {
        ToolCallingChatOptions.Builder chatOptionsBuilder = ToolCallingChatOptions.builder()
                .model(REASONING_MODEL)
                .temperature(TEMPERATURE)
                .topP(TOP_P)
                .frequencyPenalty(FREQUENCY_PENALTY)
                .presencePenalty(PRESENCE_PENALTY);
        return ChatClient.builder(chatModel)
                .defaultOptions(chatOptionsBuilder)
                .defaultAdvisors(queryAnalysisGuardrailAdvisor,
                        productSearchRetrievalAdvisor,
                        emptyRetrievalAdvisor,
                        productSelectionSchemaValidationAdvisor,
                        loggingAdvisor)
                .build();
    }

    private static String loadSystemInstructions() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/query-analysis.md");
            return new String(resource.getInputStream().readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load system instructions", e);
        }
    }
}
