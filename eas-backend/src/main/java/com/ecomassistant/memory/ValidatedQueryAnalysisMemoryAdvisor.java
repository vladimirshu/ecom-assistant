package com.ecomassistant.memory;

import com.ecomassistant.exception.IllegalAssistantResponseException;
import com.ecomassistant.guardrails.QueryAnalysis;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Adds history to query analysis and commits a complete turn only after validation. */
public final class ValidatedQueryAnalysisMemoryAdvisor implements CallAdvisor {
    private static final JsonMapper JSON = JacksonUtils.getDefaultJsonMapper();
    private static final Schema SCHEMA = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(JSON.readTree(JsonSchemaGenerator.generateForType(QueryAnalysis.class)));
    private final ChatMemory memory;

    public ValidatedQueryAnalysisMemoryAdvisor(ChatMemory memory) {
        this.memory = memory;
    }

    @Override
    public String getName() {
        return "Validated query analysis memory";
    }

    @Override
    public int getOrder() {
        return Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String id = Objects.requireNonNull(request.context().get(ChatMemory.CONVERSATION_ID),
                "conversationId is required").toString();
        Message user = request.prompt().getLastUserOrToolResponseMessage();
        List<Message> messages = new ArrayList<>(memory.get(id));
        messages.addAll(request.prompt().getInstructions());
        // OpenAI requires the system message to precede the conversation.
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof SystemMessage) {
                messages.add(0, messages.remove(i));
                break;
            }
        }
        ChatClientRequest withHistory = request.mutate()
                .prompt(request.prompt().mutate().messages(messages).build()).build();
        ChatClientResponse response = chain.nextCall(withHistory);

        if (response.chatResponse() == null || response.chatResponse().getResult() == null
                || response.chatResponse().getResult().getOutput() == null) {
            throw new IllegalAssistantResponseException("Query analysis response is missing content", "empty response");
        }
        Message assistant = response.chatResponse().getResult().getOutput();
        String content = assistant.getText();
        try {
            if (content == null || !SCHEMA.validate(JSON.readTree(content)).isEmpty()) {
                throw new IllegalAssistantResponseException(
                        "Query analysis response does not match the expected schema", "invalid schema");
            }
            // Conversion can fail even for syntactically valid JSON. Do not commit in that case.
            new BeanOutputConverter<>(QueryAnalysis.class).convert(content);
        } catch (IllegalAssistantResponseException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalAssistantResponseException(
                    "Query analysis response does not match the expected schema", e.getMessage());
        }
        memory.add(id, List.of(user, assistant));
        return response;
    }
}
