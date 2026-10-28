package com.ecomassistant.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmptyRetrievalAdvisorTest {

    private final EmptyRetrievalAdvisor advisor = new EmptyRetrievalAdvisor();

    @Test
    void emptyDocumentsReturnSyntheticResponseWithoutCallingNext() {
        ChatClientRequest request = request(Map.of(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, List.of()));
        CallAdvisorChain chain = mock(CallAdvisorChain.class);

        ChatClientResponse response = advisor.adviseCall(request, chain);

        verify(chain, never()).nextCall(request);
        assertThat(response.chatResponse().getResult().getOutput().getText()).isEqualTo("{\"skus\":[]}");
    }

    @Test
    void missingDocumentContextReturnsSyntheticResponseWithoutCallingNext() {
        ChatClientRequest request = request(Map.of());
        CallAdvisorChain chain = mock(CallAdvisorChain.class);

        ChatClientResponse response = advisor.adviseCall(request, chain);

        verify(chain, never()).nextCall(request);
        assertThat(response.chatResponse().getResult().getOutput().getText()).isEqualTo("{\"skus\":[]}");
    }

    @Test
    void nonEmptyDocumentsDelegateToNext() {
        ChatClientRequest request = request(Map.of(
                RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT,
                List.of(Document.builder().id("a").text("a").metadata(Map.of("sku", "A")).build())));
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        ChatClientResponse expected = ChatClientResponse.builder().build();
        when(chain.nextCall(request)).thenReturn(expected);

        ChatClientResponse response = advisor.adviseCall(request, chain);

        verify(chain).nextCall(request);
        assertThat(response).isSameAs(expected);
    }

    private static ChatClientRequest request(Map<String, Object> context) {
        Prompt prompt = Prompt.builder().messages(UserMessage.builder().text("query").build()).build();
        return ChatClientRequest.builder().prompt(prompt).context(context).build();
    }
}
