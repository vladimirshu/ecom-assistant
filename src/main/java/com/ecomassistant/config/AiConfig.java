package com.ecomassistant.config;

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
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

@Configuration
public class AiConfig {
    //    public static final String REASONING_MODEL = "qwen3:0.6b";
    public static final String REASONING_MODEL = "gpt-4o-mini";
    public static final String EMBEDDING_MODEL = "nomic-embed-text:latest";

    @Bean
    public ChatMemoryRepository chatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder().maxMessages(5).chatMemoryRepository(chatMemoryRepository).build();
    }

    @Bean
    public Advisor loggingAdvisor() {
        return new SimpleLoggerAdvisor(ChatClientRequest::toString, ChatResponse::toString, 0);
    }

    @Bean
    public ChatClient assistantChatClient(ChatModel chatModel, ChatMemory chatMemory, AssistantTools assistantTools) {
        ChatOptions chatOptions = ToolCallingChatOptions.builder()
                .model(REASONING_MODEL)
//                .internalToolExecutionEnabled(false)
                .build();
        return ChatClient.builder(chatModel)
                .defaultOptions(chatOptions)
                .defaultTools(assistantTools)
                .defaultSystem(loadSystemInstructions())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build()).build();
    }

    //    @Bean
    //    public ChatClient chatClient(ChatMemory chatMemory, ChatClient.Builder chatClientBuilder,
    //                                 AssistantTools assistantTools) {
    //        return chatClientBuilder.defaultOptions(ChatOptions.builder().model(REASONING_MODEL).build())
    //                .defaultSystem(SYSTEM_INSTRUCTION)
    //                .defaultTools(assistantTools)
    //                .defaultAdvisors(loggingAdvisor(), MessageChatMemoryAdvisor.builder(chatMemory).build()).build();
    //    }

    private static String loadSystemInstructions() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/system-instructions.md");
            return new String(resource.getInputStream().readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load system instructions", e);
        }
    }

}
