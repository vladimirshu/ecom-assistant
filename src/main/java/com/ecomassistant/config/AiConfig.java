package com.ecomassistant.config;

import com.ecomassistant.advisors.LlmOutputValidationAdvisor;
import com.ecomassistant.tools.AssistantTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
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
    public Advisor loggingAdvisor() {
        return new SimpleLoggerAdvisor(ChatClientRequest::toString, ChatResponse::toString, 0);
    }

    @Bean
    public Advisor llmOutputValidationAdvisor() {
        return new LlmOutputValidationAdvisor();
    }

    @Bean
    public ChatClient assistantChatClient(ChatModel chatModel, ChatMemory chatMemory, AssistantTools assistantTools) {
        ToolCallingChatOptions.Builder chatOptionsBuilder = ToolCallingChatOptions.builder()
                .model(REASONING_MODEL)
                .temperature(TEMPERATURE)
                .topP(TOP_P)
                .frequencyPenalty(FREQUENCY_PENALTY)
                .presencePenalty(PRESENCE_PENALTY);
        return ChatClient.builder(chatModel)
                .defaultOptions(chatOptionsBuilder)
                .defaultTools(assistantTools)
                .defaultSystem(loadSystemInstructions())
                .defaultAdvisors(loggingAdvisor(),
                        llmOutputValidationAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()).build();
    }

    private static String loadSystemInstructions() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/system-instructions.md");
            return new String(resource.getInputStream().readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load system instructions", e);
        }
    }
}