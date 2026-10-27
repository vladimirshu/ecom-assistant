package com.ecomassistant.advisors;

import com.ecomassistant.validation.PlainTextPolicy;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

public class LlmOutputValidationAdvisor implements CallAdvisor {
    private static final int MAX_ASSISTANT_RESPONSE_LENGTH = 4000;

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);
        PlainTextPolicy.requirePlainText(
                response.chatResponse().getResult().getOutput().getText(), MAX_ASSISTANT_RESPONSE_LENGTH);
        return response;
    }

    @Override
    public String getName() {
        return LlmOutputValidationAdvisor.class.getSimpleName();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
