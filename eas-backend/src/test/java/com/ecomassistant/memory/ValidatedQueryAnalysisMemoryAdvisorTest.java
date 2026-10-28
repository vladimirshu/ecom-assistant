package com.ecomassistant.memory;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValidatedQueryAnalysisMemoryAdvisorTest {
    @Test
    void modelFailureDoesNotChangeHistory() {
        ChatMemory memory = MessageWindowChatMemory.builder().build();
        memory.add("session", new UserMessage("previous"));
        var original = List.copyOf(memory.get("session"));
        var advisor = new ValidatedQueryAnalysisMemoryAdvisor(memory);
        var request = new ChatClientRequest(new Prompt(new UserMessage("failed request")),
                Map.of(ChatMemory.CONVERSATION_ID, "session"));
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any())).thenThrow(new IllegalStateException("model unavailable"));

        assertThatThrownBy(() -> advisor.adviseCall(request, chain))
                .isInstanceOf(IllegalStateException.class);
        assertThat(memory.get("session")).isEqualTo(original);
    }
}
