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

class DirectRetrievalAdvisorTest {

    private final DirectRetrievalAdvisor advisor = new DirectRetrievalAdvisor();

    @Test
    void returnsRetrievedDocumentsInResponseMetadataWithoutCallingNext() {
        List<Document> documents = List.of(
                Document.builder().id("a").text("a").metadata(Map.of("sku", "A")).build(),
                Document.builder().id("b").text("b").metadata(Map.of("sku", "B")).build());
        ChatClientRequest request = request(Map.of(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, documents));
        CallAdvisorChain chain = mock(CallAdvisorChain.class);

        ChatClientResponse response = advisor.adviseCall(request, chain);

        verify(chain, never()).nextCall(request);
        assertThat(returnedDocuments(response)).isEqualTo(documents);
        assertThat(response.context()).containsEntry(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, documents);
    }

    @Test
    void missingDocumentContextReturnsEmptyListWithoutCallingNext() {
        ChatClientRequest request = request(Map.of());
        CallAdvisorChain chain = mock(CallAdvisorChain.class);

        ChatClientResponse response = advisor.adviseCall(request, chain);

        verify(chain, never()).nextCall(request);
        assertThat(returnedDocuments(response)).isEmpty();
    }

    @Test
    void emptyDocumentContextReturnsEmptyListWithoutCallingNext() {
        ChatClientRequest request = request(Map.of(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, List.of()));
        CallAdvisorChain chain = mock(CallAdvisorChain.class);

        ChatClientResponse response = advisor.adviseCall(request, chain);

        verify(chain, never()).nextCall(request);
        assertThat(returnedDocuments(response)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static List<Document> returnedDocuments(ChatClientResponse response) {
        Object value = response.chatResponse().getMetadata().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);
        return value instanceof List<?> list ? (List<Document>) list : List.of();
    }

    private static ChatClientRequest request(Map<String, Object> context) {
        Prompt prompt = Prompt.builder().messages(UserMessage.builder().text("query").build()).build();
        return ChatClientRequest.builder().prompt(prompt).context(context).build();
    }
}
