package com.ecomassistant.advisors;

import com.ecomassistant.utils.ValidationUtils;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

public class LlmOutputValidationAdvisor implements CallAdvisor {
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);
        ValidationUtils.validateAssistantMessage(response.chatResponse().getResult().getOutput());
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
